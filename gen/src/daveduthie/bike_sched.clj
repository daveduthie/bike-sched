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

(s/def :resource/quantity pos-int?)

(s/def :resource/cost nat-int?)

(s/def :resource/name (s/and string? not-empty))

(s/def :project/resource (s/tuple :resource/name :resource/cost))

(s/def :mode/requirements
  (s/every (s/tuple :resource/quantity uuid?)
           :distinct  true
           :min-count 1
           :gen-max   3))

(s/def :task/mode
       (s/keys :req [:mode/duration :mode/requirements]))

(s/def :task/modes
       (s/map-of uuid? :task/mode :distinct true :min-count 1))

(s/def :task/deps (s/coll-of uuid? :gen-max 3))

(s/def :project/task (s/keys :req [:task/deps :task/modes]))

(s/def :project/tasks (s/map-of uuid? :project/task))

(s/def :project/resources (s/map-of uuid? :project/resource))

(s/def :project/schedule (s/keys :req [:project/tasks :project/resources]))

;;; Having another go at this. Binding the way to victory...

(defn- deps*
  [possible-deps]
  (if (not-empty possible-deps)
    (gen/vector (gen/elements possible-deps) 0 3)
    (gen/return [])))

(defn- resources*
  [resources]
  (->>
   (gen/map gen/uuid
            (s/gen :project/resource)
            {:min-elements (max 0 (- 4 (count resources)))})
   (gen/fmap (partial merge resources))))

(defn- modes*
  [possible-resources]
  (gen/map gen/uuid
           (gen/fmap
            (fn [[duration requirements]]
              {:mode/duration     duration,
               :mode/requirements requirements})
            (gen/tuple (gen/choose 1 720)
                       (gen/vector-distinct-by
                        second
                        (gen/tuple (gen/choose 1 10)
                                   (gen/elements possible-resources))
                        {:min-elements 1})))
           {:min-elements 1}))

#_(s/explain
   :task/modes
   (first
    (gen/sample
     (modes*
      (keys
       {#uuid "8ee5ab84-7750-4e3d-974b-d4e755d1a05c" 1,
        #uuid "e910e18f-780d-4afd-abee-335c5d638cbf" 2})))))


(defn add-task*
  [project-seed*]
  (gen/bind
   project-seed*
   (fn [project]
     (gen/bind
      (resources* (:project/resources project))
      (fn [resources]
        (gen/fmap
         (fn [[deps modes tid]]
           (when (empty? modes)
             (throw
               (ex-info "huh?"
                        {:deps      deps,
                         :modes     modes,
                         :resources resources,
                         :tid       tid})))
           (-> project
               (assoc :project/resources resources)
               (assoc-in [:project/tasks tid]
                         {:task/deps deps, :task/modes modes})))
         (gen/tuple (deps* (keys (:project/tasks project)))
                    (modes* (keys resources))
                    gen/uuid)))))))

(def project-seed*
  (gen/return
   {:project/resources {}, :project/tasks {}}))

(def project-gen* (iterate add-task* project-seed*))

(defn sample-project
  ([size] (gen/generate (nth project-gen* size) size))
  ([size seed] (gen/generate (nth project-gen* size) size seed)))

(comment
  ;;; Experimenting with ints as ids. Quite like how it makes the
  ;;; schedule more compact.
  (let [p      (sample-project 2 2)
        ctr    (volatile! -1)
        lookup (volatile! {})
        ->int  (fn [id]
                 (if-let [int-id (get @lookup id)]
                   int-id
                   (let [int-id (vswap! ctr inc)]
                     (vswap! lookup assoc id int-id)
                     int-id)))
        p'     (-> p
                   (update
                    :project/resources
                    (fn [resources]
                      (into []
                            (map val)
                            (walk/postwalk (fn [x]
                                             (if (uuid? x) (->int x) x))
                                           resources))))
                   (update :project/tasks
                           (fn [tasks]
                             (walk/postwalk
                              (fn [x] (if-let [int-id (@lookup x)]
                                        int-id x))
                              tasks))))
        p''    (-> p'
                   (update :project/tasks
                           (fn [tasks]
                             (into {}
                                   (map (fn [[task-id task]]
                                          [task-id
                                           (update task :task/modes
                                                   (fn [modes]
                                                     (into []
                                                           (vals modes))))]))
                                   tasks))))
        _      (vreset! ctr -1)
        p'''   (update p'' :project/tasks
                       (fn [tasks]
                         (into []
                               (map val)
                               (walk/postwalk (fn [x]
                                                (if (uuid? x)
                                                  (->int x)
                                                  x))
                                              tasks))))]
    p''')

  {:project/resources [["Ni" 1] ["A" 2] ["0" 1] ["yD" 0] ["i" 0] ["2" 1]],
   :project/tasks
   [{:task/deps  [],
     :task/modes [{:mode/duration     17,
                   :mode/requirements [{:req/id 3, :req/quant 10}]}
                  {:mode/duration     577,
                   :mode/requirements [{:req/id 3, :req/quant 5}]}
                  {:mode/duration     641,
                   :mode/requirements [{:req/id 1, :req/quant 6}]}]}
    {:task/deps  [0],
     :task/modes [{:mode/duration     647,
                   :mode/requirements [{:req/id 4, :req/quant 6}]}
                  {:mode/duration     3,
                   :mode/requirements [{:req/id 2, :req/quant 10}
                                       {:req/id 0, :req/quant 8}
                                       {:req/id 5, :req/quant 3}]}]}]})

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
  (let [data         (sample-project 15 10)
        roundtripped (-> data
                         json/encode
                         (json/decode true))]
    (-> (post-schedule! data) :genotype))
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
