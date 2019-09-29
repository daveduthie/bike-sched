(ns daveduthie.bike-sched
  (:require [clj-http.client :as clj-http]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [expound.alpha :as expound]
            [loom.graph :as graph]
            [loom.label]
            loom.io))

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

#_(s/explain :project/schedule
             (nth (gen/sample-seq (nth project-gen* 1)) 10))

#:project{:resources {#uuid "d8f18b9f-bce2-494a-80c1-5565d041fe75" ["1xukwN6X0j" 1],
                      #uuid "e535d21e-eabe-4ce1-b2e6-b3c5254edfcf" ["KHcDNC608" 8],
                      #uuid "baef6179-61bb-4141-8d8c-beda76e27fc9" ["Eu2" 334],
                      #uuid "ae343099-3a7a-4424-b456-ce6776f2a5d9" ["Ye24" 2],
                      #uuid "11402437-b2bf-435f-927b-066d034706f7" ["70j76w" 221],
                      #uuid "f6c9369f-2364-4e9f-a616-4087f4015354" ["ley" 0],
                      #uuid "2342d3b5-99cb-4039-99e5-346aa8dc7e0b" ["3t6" 60],
                      #uuid "cd8afe58-d427-4127-b658-be1ca5fd4843" ["9r1WyrffPg" 1]},
          :tasks     {#uuid "30678e34-1d23-48a5-9cce-62312ef39f5d"
                      #:task{:deps [],
                             :modes
                             {#uuid "8c2a265f-f68f-43ca-921f-af30c8cbcfdf"
                              #:mode{:duration     380,
                                     :requirements #{[10 #uuid "f6c9369f-2364-4e9f-a616-4087f4015354"]
                                                     [3 #uuid "11402437-b2bf-435f-927b-066d034706f7"]
                                                     [9 #uuid "cd8afe58-d427-4127-b658-be1ca5fd4843"]
                                                     [8 #uuid "d8f18b9f-bce2-494a-80c1-5565d041fe75"]
                                                     [8 #uuid "ae343099-3a7a-4424-b456-ce6776f2a5d9"]
                                                     [7 #uuid "baef6179-61bb-4141-8d8c-beda76e27fc9"]
                                                     [4 #uuid "e535d21e-eabe-4ce1-b2e6-b3c5254edfcf"]
                                                     [5 #uuid "2342d3b5-99cb-4039-99e5-346aa8dc7e0b"]}},
                              #uuid "151b9906-a43e-45bd-85e7-fc016ba20acf"
                              #:mode{:duration     179,
                                     :requirements #{[3 #uuid "cd8afe58-d427-4127-b658-be1ca5fd4843"]}},
                              #uuid "c88655e6-64e9-4fad-85f1-ee315c65c49a"
                              #:mode{:duration     472,
                                     :requirements #{[10 #uuid "baef6179-61bb-4141-8d8c-beda76e27fc9"]
                                                     [5 #uuid "cd8afe58-d427-4127-b658-be1ca5fd4843"]
                                                     [3 #uuid "11402437-b2bf-435f-927b-066d034706f7"]
                                                     [8 #uuid "d8f18b9f-bce2-494a-80c1-5565d041fe75"]
                                                     [9 #uuid "f6c9369f-2364-4e9f-a616-4087f4015354"]
                                                     [5 #uuid "e535d21e-eabe-4ce1-b2e6-b3c5254edfcf"]
                                                     [4 #uuid "2342d3b5-99cb-4039-99e5-346aa8dc7e0b"]}},
                              #uuid "39defdc8-cfb6-4684-aaf1-5433972ff3dd"
                              #:mode{:duration     78,
                                     :requirements #{[3 #uuid "2342d3b5-99cb-4039-99e5-346aa8dc7e0b"]
                                                     [7 #uuid "f6c9369f-2364-4e9f-a616-4087f4015354"]
                                                     [7 #uuid "d8f18b9f-bce2-494a-80c1-5565d041fe75"]
                                                     [7 #uuid "baef6179-61bb-4141-8d8c-beda76e27fc9"]
                                                     [5 #uuid "e535d21e-eabe-4ce1-b2e6-b3c5254edfcf"]}},
                              #uuid "f7a6bd7a-4fcd-4f04-8d9b-d23f5747f991"
                              #:mode{:duration     154,
                                     :requirements #{[8 #uuid "f6c9369f-2364-4e9f-a616-4087f4015354"]
                                                     [2 #uuid "baef6179-61bb-4141-8d8c-beda76e27fc9"]
                                                     [7 #uuid "d8f18b9f-bce2-494a-80c1-5565d041fe75"]
                                                     [4 #uuid "e535d21e-eabe-4ce1-b2e6-b3c5254edfcf"]
                                                     [2 #uuid "2342d3b5-99cb-4039-99e5-346aa8dc7e0b"]}}}}}}


(defn sample-project
  ([size] (gen/generate (nth project-gen* size) size))
  ([size seed] (gen/generate (nth project-gen* size) size seed)))

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
