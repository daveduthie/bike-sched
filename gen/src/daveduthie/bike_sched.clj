(ns daveduthie.bike-sched
  (:require [clj-http.client :as clj-http]
            [clojure.java.io :as io]
            [jsonista.core :as json]
            [clojure.pprint :as pprint]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [clojure.walk :as walk]
            [expound.alpha :as expound]
            [loom.graph :as graph]
            loom.io
            loom.label))

(def json-object-mapper (json/object-mapper {:decode-key-fn true :pretty true}))

(set! *print-namespace-maps* false)
(set! s/*explain-out* expound/printer)

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
   1))

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
  ([size] (gen/generate (nth project-gen* size) size))
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

(comment
  ;; Visualise dependencies between tasks
  (loom.io/view
   (reduce-kv
    (fn [graph task-id {:task/keys [deps]}]
      (->> deps
           (map (fn [dep] [dep task-id])) ; dep->task-id
           (apply graph/add-edges #_loom.label/add-labeled-edges graph)))
    (graph/digraph)
    (:project/tasks (sample-project 25 5))))

  (sample-project 1 2)
  :.)

(comment
  ;; Visualise resource contention
  (let [{:project/keys [tasks]} (sample-project 5 5)]
    (loom.io/view
     (reduce-kv
      (fn [graph _task-id {:task/keys [modes]}]
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

(comment
  (-> (clj-http/get "http://localhost:8000")
      :body)
  (let [data (sample-project 15 10)]
    (-> (post-schedule! data))))
