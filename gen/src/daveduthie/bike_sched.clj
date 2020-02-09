(ns daveduthie.bike-sched
  (:require [clj-http.client :as clj-http]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [expound.alpha :as expound]
            [loom.graph :as graph]
            [loom.label]
            loom.io
            [clojure.walk :as walk]))

(set! *print-namespace-maps* false)
(set! s/*explain-out* expound/printer)

(s/def :mode/duration pos-int?)

(s/def :requirement/quant pos-int?)

(s/def :resource/cost nat-int?)

(s/def :resource/name (s/and string? not-empty))

(s/def :project/resource
  (s/keys :req [:resource/name :resource/cost]))

(s/def :project/resources (s/every :project/resource))

(s/def :requirement/res-id nat-int?)

(s/def :mode/requirement
  (s/keys :req [:requirement/quant :requirement/res-id]))

(s/def :mode/requirements
  (s/every :mode/requirement
           :distinct  true ; could generate duplicate `:requirement/res-id`s :/
           :min-count 1
           :gen-max   3))

(s/def :task/mode
       (s/keys :req [:mode/duration :mode/requirements]))

(s/def :task/modes
       (s/every :task/mode :distinct true :min-count 1))

(s/def :task/deps (s/coll-of nat-int? :gen-max 3))

(s/def :project/task (s/keys :req [:task/deps :task/modes]))

(s/def :project/tasks (s/every :project/task))

(s/def :project/schedule (s/keys :req [:project/tasks :project/resources]))

;;; Having another go at this. Binding the way to victory...

(defn- deps*
  [possible-deps]
  (let [possible-dep-ids (range (count possible-deps))]
    (if (not-empty possible-dep-ids)
      (gen/vector (gen/elements possible-dep-ids) 0 3)
      (gen/return []))))

(defn- resources*
  [resources]
  (->>
   (gen/vector-distinct-by :resource/name
                           (s/gen :project/resource)
                           {:min-elements (max 0 (- 4 (count resources)))})
   (gen/fmap (partial into resources))))

#_(s/explain :project/resources
             (gen/generate (resources* []) 10 1000))

(defn- modes*
  [possible-resources]
  (let [possible-resource-ids (range (count possible-resources))]
    (gen/vector-distinct-by
     :mode/requirements
     (gen/fmap
      (partial zipmap [:mode/duration :mode/requirements])
      (gen/tuple
       (gen/choose 1 720)
       (gen/vector-distinct-by
        :requirement/res-id
        (gen/fmap (partial zipmap [:requirement/quant :requirement/res-id])
                  (gen/tuple (gen/choose 1 10)
                             (gen/elements possible-resource-ids)))
        {:min-elements 1})))
     {:min-elements 1})))

#_(s/explain :task/modes
             (gen/generate (modes* [:foo :bar]) 2 2))

(defn add-task*
  [project-seed*]
  (gen/bind
   project-seed*
   (fn [project]
     (gen/bind
      (resources* (:project/resources project))
      (fn [resources]
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
               (update :project/tasks conj
                       {:task/deps deps, :task/modes modes})))
         (gen/tuple (deps* (:project/tasks project))
                    (modes* resources))))))))

(def project-seed*
  (gen/return
   {:project/resources [], :project/tasks []}))

(def project-gen* (iterate add-task* project-seed*))

(defn sample-project
  ([size] (gen/generate (nth project-gen* size) size))
  ([size seed] (gen/generate (nth project-gen* size) size seed)))

(comment (s/explain :project/schedule (sample-project 1 1))
         (sample-project 1 1)
         {:project/resources [{:resource/cost 1, :resource/name "M"}
                              {:resource/cost 1, :resource/name "i"}
                              {:resource/cost 1, :resource/name "sL"}
                              {:resource/cost 0, :resource/name "a"}
                              {:resource/cost 0, :resource/name "b"}],
          :project/tasks [{:task/deps  [],
                           :task/modes [{:mode/duration 179,
                                         :mode/requirements
                                         [{:requirement/quant  1,
                                           :requirement/res-id 2}
                                          {:requirement/quant  9,
                                           :requirement/res-id 3}]}]}]})

(defn post-schedule! [schedule]
  (:body
   (clj-http/post "http://localhost:8000/schedule"
                  {:as                :json,
                   :content-type      :json,
                   :form-params       schedule,
                   :throw-exceptions? false})))


(comment
  (-> (clj-http/get "http://localhost:8000")
      :body)
  (require '[cheshire.core :as json])

  (let [data (sample-project 15 10)]
    (-> (post-schedule! data) :genotype))

  (json/gener)
  (sample-project 5 5)

  (defonce keep-going (atom true))

  (reset! keep-going false)
  
  (.start (Thread.
           (fn []
             (while @keep-going
               (Thread/sleep 5000)
               (let [data (sample-project 15 10)]
                 (-> (post-schedule! data) :genotype))))))

  ;; Visualise dependencies between tasks
  (loom.io/view
   (reduce-kv
    (fn [graph task-id {:task/keys [deps]}]
      (->> deps
           (map (fn [dep] [dep task-id])) ; dep->task-id
           (apply graph/add-edges #_loom.label/add-labeled-edges graph)))
    (graph/digraph)
    (:project/tasks (sample-project 25 5))))


  :.)

(comment
  ;; Visualise resource contention
  (let [{:project/keys [tasks]} (sample-project 5 5)]
    (loom.io/view
     (reduce-kv
      (fn [graph task-id {:task/keys [modes]}]
        (reduce-kv
         (fn [graph mode-id {:mode/keys [requirements]}]
           (->> requirements
                (mapcat (fn [[_ resource-id]]
                          [[mode-id resource-id] "M -> R"]))
                (apply loom.label/add-labeled-edges graph)))
         graph modes))
      (graph/digraph)
      tasks)
     ;; :alg :sfdp
     :alg :circo)))
