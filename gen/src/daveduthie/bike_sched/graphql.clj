(ns daveduthie.bike-sched.graphql
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.walk :as walk]
            [com.walmartlabs.lacinia :as lacinia]
            [com.walmartlabs.lacinia.resolve :as lacinia.resolve]
            [com.walmartlabs.lacinia.schema :as lacinia.schema]
            [com.walmartlabs.lacinia.util :as lacinia.util]
            [daveduthie.bike-sched.gen :as gen]))

(defn- resolve-project
  [_ {:keys [size seed]} _]
  (let [project (gen/sample-project size seed)]
    (lacinia.resolve/with-context project {:project project})))

(defn- add-ids [xs] (map-indexed (fn [i m] (assoc m :id i)) xs))

(defn- resolve-task-deps
  [{:keys [project]} _ task]
  (prn ::Task task)
  (prn ::Project-ts (:project/tasks project))
  (let [ret
        (add-ids
         (map (fn [id] (-> project :project/tasks (get id)))
              (:task/deps task)))]
    (prn :ret (map :id ret) ret)
    ret))

(defn- resolve-mode-requirement-resource
  [{:keys [project]} _ mode-requirement]
  (get-in project [:project/resources (:req/id mode-requirement)]))

(defn- getter [k] (fn [_ _ value] (get value k)))

(def compiled-schema
  (-> (io/resource "graphql/schema.edn")
      slurp
      edn/read-string
      (lacinia.util/inject-resolvers {
                                      :Mode/duration            (getter :mode/duration)
                                      :Mode/requirements        (getter :mode/requirements)
                                      :ModeRequirement/resource resolve-mode-requirement-resource
                                      :ModeRequirement/quant    (getter :req/quant)
                                      :Project/resources        (comp add-ids (getter :project/resources))
                                      :Project/tasks            (comp add-ids (getter :project/tasks))
                                      :Resource/cost            (getter :resource/cost)
                                      :Resource/name            (getter :resource/name)
                                      :Resource/quantity        (getter :resource/quantity)
                                      :Task/deps                resolve-task-deps
                                      :Task/modes               (getter :task/modes)
                                      :queries/Project          resolve-project
                                      })
      lacinia.schema/compile))

(defn execute [request query variables operation-name]
   (lacinia/execute compiled-schema query variables
                    {:request                 request
                     ::lacinia/enable-timing? true}
                    {:operation-name operation-name}))

(defn simplify [result]
  (walk/postwalk (fn [x] (if (map? x) (into {} x) x))
                 result))

(comment
  (:body Request)
  (simplify
   (execute {}
            "query foo { Project { x } }"
            {}
            nil)))
