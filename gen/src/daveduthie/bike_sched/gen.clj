(ns daveduthie.bike-sched.gen
  (:require [clj-http.client :as clj-http]
            [clojure.java.io :as io]
            [jsonista.core :as json]
            [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [expound.alpha :as expound]
            [loom.graph :as graph]
            loom.io
            loom.label))

(def json-object-mapper (json/object-mapper {:decode-key-fn true :pretty true}))

(alter-var-root #'*print-namespace-maps* (constantly false))
(alter-var-root #'s/*explain-out* (constantly expound/printer))

(s/def :mode/duration pos-int?)

(s/def :resource/quantity pos-int?)

(s/def :resource/cost nat-int?)

(s/def :resource/name (s/and string? not-empty))

(s/def :project/resource
  (s/keys :req [:resource/name :resource/cost :resource/quantity]))

(s/def :req/id nat-int?)
(s/def :req/quant pos-int?)

(s/def :mode/requirements
  (s/every (s/keys :req [:req/id :req/quant])
           :distinct  true
           :min-elements 1
           :gen-max   3))

(s/def :task/mode
       (s/keys :req [:mode/duration :mode/requirements]))

(s/def :task/modes
  (s/coll-of :task/mode :distinct true :min-elements 1))

(s/def :task/deps (s/coll-of nat-int? :gen-max 3))

(s/def :project/task (s/keys :req [:task/deps :task/modes]))

(s/def :project/tasks (s/coll-of :project/task))

(s/def :project/resources (s/coll-of :project/resource))

(s/def :project/schedule (s/keys :req [:project/tasks :project/resources]))

;;; Having another go at this. Binding the way to victory...

(defn- deps*
  [n-possible-deps]
  (if (> n-possible-deps 0)
    (gen/set (gen/elements (range n-possible-deps)) {:min-elements 0 :max-count 3})
    (gen/return [])))

(defn- resources*
  [resources]
  (let [min-elements (max 0 (- 4 (count resources)))]
    (gen/fmap (partial into resources)
              (gen/vector-distinct
                (s/gen :project/resource)
                {:min-elements min-elements}))))

(comment
  (gen/sample (resources* []) 5))

(defn- modes*
  [n-possible-resources]
  (assert (> n-possible-resources 0) {:huh n-possible-resources})
  (gen/vector
   (gen/fmap
    (fn [[duration requirements]]
      {:mode/duration     duration,
       :mode/requirements requirements})
    (gen/tuple (gen/choose 1 720)
               (gen/vector-distinct-by
                :req/id
                (gen/fmap (fn [[id quant]]
                            {:req/id id :req/quant quant})
                          (gen/tuple (gen/elements (range n-possible-resources))
                                     (gen/choose 1 10)))
                {:min-elements 1})))
   1 ; min modes
   3 ; max modes
   ))

(defn add-task*
  [project-seed*]
  (gen/bind
   project-seed*
   (fn [project]
     (gen/bind
      (resources* (:project/resources project))
      (fn [resources]
        (assert (> (count resources) 0) {:huh resources})
        (gen/fmap
         (fn [[deps modes]]
           (when (empty? modes)
             (throw
              (ex-info "huh?"
                       {:deps      deps,
                        :modes     modes,
                        :resources resources,})))
           (-> project
               (assoc :project/resources resources)
               (update :project/tasks conj {:task/deps deps, :task/modes modes})))
         (gen/tuple (deps* (count (:project/tasks project)))
                    (modes* (count resources)))))))))

(def project-seed*
  (gen/return
   {:project/resources [], :project/tasks []}))

(def project-gen* (iterate add-task* project-seed*))

(defn sample-project
  ;; doesn't specify seed, we we get a random schedule of `size`.
  ([size] (gen/generate (nth project-gen* size) size))
  ;; specify seed, for reproducibility
  ([size seed] (gen/generate (nth project-gen* size) size seed)))

(defn dump-project
  [project file-name]
  (let [edn? (re-find #"\.edn$" file-name)]
    (with-open [w (io/writer file-name)]
      (if edn?
        (pprint/pprint project w)
        (json/write-value w project json-object-mapper)))))

(comment
  (dump-project
   (sample-project 2 2)
   "sample2-2.edn")

  (dump-project
   (sample-project 10 10)
   "sample2-2.json")

  (sample-project 10 10))

(defn post-schedule! [schedule]
  (-> (clj-http/post "http://localhost:8000/schedule"
                     {:content-type      :json,
                      :body              (json/write-value-as-bytes schedule),
                      :throw-exceptions? false})
      #_(update :body json/read-value)
      (select-keys [:body :status])))

(defn resource-contention
  [project]
  (let [resource-nodes (map-indexed
                        (fn [idx resource]
                          {:nodes [{:id (str "R " idx),
                                    :label (:resource/name resource),
                                    :data {:type "earth"}}]})
                        (:project/resources project))
        other-things
        (apply
         concat
         (map-indexed
          (fn [task-id {:task/keys [modes]}]
            (let [Task (str "T " task-id)]
              (apply
               concat
               [{:nodes [{:id Task,
                          :label (str "Task " task-id),
                          :data {:type "check-square"}}]}]
               (map-indexed
                (fn [mode-id {:mode/keys [requirements]}]
                  (let [Mode (format "M %s::%s" task-id mode-id)]
                    (into
                     [{:nodes [{:id Mode,
                                ;; :type "diamond",
                                :label (str "Mode " mode-id),
                                :data {:type "company"}}],
                       :edges [{:source Task,
                                :target (format "M %s::%s" task-id mode-id)
                                :data {:source Task,
                                       :target (format "M %s::%s" task-id mode-id)}}]}]
                     (map (fn [{:req/keys [id quant]}]
                            {:nodes [],
                             :edges [{:source Mode,
                                      :target (str "R " id),
                                      :data {:source Mode,
                                             :target (str "R " id)}
                                      :style {:lineWidth quant}}]})
                          requirements))))
                modes))))
          (-> project
              :project/tasks)))]
    {:edges (mapcat :edges other-things),
     :nodes (concat (mapcat :nodes resource-nodes)
                    (mapcat :nodes other-things))}))

(comment
  (resource-contention (sample-project 1 0))

  )

(comment
  (let [p  (sample-project 2 1)
        ts (:project/tasks p)]
    (future
      (loom.io/view
       ;; Visualise resource contention
       (apply
        loom.label/add-labeled-edges
        (graph/digraph)
        (apply
         concat
         (map-indexed
          (fn [task-id {:task/keys [modes]}]
            (prn :modes (count modes))
            (apply concat
                   (map-indexed
                    (fn [mode-id {:mode/keys [requirements]}]
                      (->> requirements
                           (mapcat (fn [{:req/keys [id quant]}]
                                     [[(format "M %s::%s" task-id mode-id)
                                       (str "R " id)]
                                      (format "x%s" quant)]))
                           (into [[(str "T " task-id)
                                   (format "M %s::%s" task-id mode-id)]
                                  "Option"])))
                    modes)))
          ts)))
       ;; :alg :sfdp
       ;; :alg :circo
       :alg :dot
       ))
    (future
      (loom.io/view
       ;; Visualise dependencies between tasks
       (reduce-kv
        (fn [graph task-id {:task/keys [deps]}]
          (->> deps
               (map (fn [dep] [dep task-id])) ; dep->task-id
               (apply graph/add-edges #_loom.label/add-labeled-edges graph)))
        (apply graph/digraph (range (count ts)))
        ts)))))

(comment
  (-> (clj-http/get "http://localhost:8000")
      :body)

  (let [data (sample-project 15 10)]
    (map :release_time
         (-> (post-schedule! data)
             :body
             (json/read-value json-object-mapper)
             :genotype)))

  (sample-project 2 2)

  #:project{:resources [#:resource{:cost 1, :name "aP", :quantity 1}
                        #:resource{:cost 1, :name "2x", :quantity 2}
                        #:resource{:cost 2, :name "D", :quantity 3}
                        #:resource{:cost 0, :name "c", :quantity 1}
                        #:resource{:cost 1, :name "3", :quantity 2}
                        #:resource{:cost 0, :name "B", :quantity 1}],
            :tasks
          [#:task{:deps [],
            :modes [#:mode{:duration 162,
                           :requirements [#:req{:id 0, :quant 2}
                                          #:req{:id 2, :quant 8}]}
                          #:mode{:duration 686,
                           :requirements [#:req{:id 1, :quant 10}
                                          #:req{:id 2, :quant 5}
                                          #:req{:id 4, :quant 1}]}
                          #:mode{:duration 464,
                           :requirements [#:req{:id 1, :quant 2}
                                          #:req{:id 4, :quant 4}]}]}
           #:task{:deps #{0},
            :modes [#:mode{:duration 643,
                           :requirements [#:req{:id 2, :quant 3}]}
                          #:mode{:duration 161,
                           :requirements [#:req{:id 0, :quant 3}
                                          #:req{:id 1, :quant 7}]}]}]}

  (defmacro time
    "Evaluates expr and prints the time it took.  Returns the value of
  expr."
    {:added "1.0"}
    [expr]
    `(let [start# (. System (nanoTime))
           ret# ~expr]
       {:elapsed (/ (double (- (. System (nanoTime)) start#)) 1000000.0),
        #_#_:ret ret#}))

)
