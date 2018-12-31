(ns daveduthie.bike-sched
  (:require [clojure.pprint :refer [pprint]]
            [clojure.set :as set]
            [clojure.spec.alpha :as s]
            [clojure.test.check.generators :as gen]
            [expound.alpha :as expound]
            [clj-http.client :as clj-http]))

(set! s/*explain-out* expound/printer)

(s/def :mode/duration pos-int?)

(s/def :resource/quantity pos-int?)

(s/def :resource/cost nat-int?)

(s/def :resource/name (s/and string? not-empty))

(s/def :project/resource (s/tuple :resource/name :resource/cost))

(s/def :mode/resources
  (s/coll-of (s/tuple :resource/quantity uuid?)
             :distinct  true
             :min-count 1
             :gen-max   3))

(s/def :task/modes
  (s/coll-of (s/keys :req [:mode/duration :mode/resources])
             :distinct true
             :min-count 1
             :gen-max  3))

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

#_(defn- all-distinct [xs]
    (not-any? #(> % 1) (vals (frequencies xs))))

(defn- resources*
  [resources]
  (->> (gen/map gen/uuid (s/gen :project/resource)
                {:min-elements (max 0 (- 4 (count resources)))})
       #_(gen/such-that #(all-distinct (vals %)))
       (gen/fmap (partial merge resources))))

(defn- modes*
  [possible-resources]
  (gen/vector (gen/elements (keys possible-resources)) 1 3))

(defn add-task*
  [project*]
  (gen/bind
   project*
   (fn [project]
     (gen/bind
      (gen/tuple (deps* (keys (:project/tasks project)))
                 (resources* (:project/resources project))
                 gen/uuid)
      (fn [[deps resources tid]]
        (gen/fmap (fn [modes]
                    (assoc-in project
                              [:project/tasks tid]
                              {:task/deps deps, :task/modes modes}))
                  (modes* resources)))))))

(defn project*
  []
  (gen/return {:project/resources {}, :project/tasks {}}))

(def project-gen (iterate add-task* (project*)))

#_(nth
   (gen/sample-seq (nth project-gen 10))
   10)

;; (add-task* (project*))

(defn sample-project
  [size seed]
  (gen/generate (add-task* (project*)) size seed))

#_(sample-project 10 10)

#_(s/explain :project/schedule (sample-project 0 0))


(comment (-> (clj-http/get "http://localhost:8000")
             :body)
         (-> (clj-http/post "http://localhost:8000/schedule"
                            {:as                :json,
                             :content-type      :json,
                             :form-params       (sample-project 10 10),
                             :throw-exceptions? false})
             :body)
         :.)
