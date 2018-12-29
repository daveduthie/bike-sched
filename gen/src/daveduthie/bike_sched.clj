(ns daveduthie.bike-sched
  (:require
   [clojure.edn :as edn]
   [clojure.set :as set]
   [clojure.spec.alpha :as s]
   [clojure.string :as str]
   [clojure.test.check :as check]
   [clojure.test.check.generators :as gen]
   [jsonista.core :as json])
  (:gen-class))

(s/def :mode/duration pos-int?)
(s/def :r/quantity pos-int?)
(s/def :r/cost pos-int?)
(s/def :r/name #{"labourer" "analyst" "philospher"})
(s/def ::resource (s/tuple :r/cost :r/name))
(s/def :mode/req
  (s/coll-of (s/tuple :r/quantity uuid?) :distinct true :gen-max 4))
(s/def ::mode (s/keys :req [:mode/duration :mode/req]))
(s/def :deps/ids (s/coll-of uuid? :gen-max 4))
(s/def :modes/ids (s/coll-of uuid? :gen-max 4))
(s/def ::task (s/keys :req [:deps/ids :modes/ids]))
(s/def :r/tasks (s/map-of uuid? ::task))
(s/def :r/resources (s/map-of uuid? ::resource))
(s/def :r/modes (s/map-of uuid? ::mode))
(s/def ::schedule (s/keys :req [:r/tasks :r/resources :r/modes]))

(defn make-task-refs-consistent-impl
  [tasks dep-ref-replacements mode-ref-replacements]
  (into {}
        (map (fn [[task-id task-val]]
               [task-id
                (-> task-val
                    (update :deps/ids #(->> %
                                            (keep dep-ref-replacements)
                                            (remove (partial = task-id))
                                            set))
                    (update :modes/ids #(->> %
                                             (keep mode-ref-replacements)
                                             set)))]))
        tasks))

(defn make-task-refs-consistent
  [{:as schedule, :r/keys [tasks modes]}]
  (let [dep-ref-replacements (zipmap (sort (mapcat :deps/ids (vals tasks)))
                                     (sort (keys tasks)))
        mode-ref-replacements (zipmap (sort (mapcat :modes/ids (vals tasks)))
                                      (sort (keys modes)))]
    (update schedule
            :r/tasks
            make-task-refs-consistent-impl
            dep-ref-replacements
            mode-ref-replacements)))

(defn make-mode-refs-consistent-impl
  [modes resource-ref-replacements]
  (into {}
        (map (fn [[mode-id mode-val]]
               [mode-id
                (update
                 mode-val
                 :mode/req
                 (fn [resource-req-list]
                   (into []
                         (keep (fn [[q rid]]
                                 (if-let [rid' (resource-ref-replacements rid)]
                                   [q rid'])))
                         resource-req-list)))]))
        modes))

(defn make-mode-refs-consistent
  [{:as schedule, :r/keys [modes resources]}]
  (let [available-resource-refs (sort (keys resources))
        current-resource-refs (sort (mapcat #(->> %
                                                  val
                                                  :mode/req (map second))
                                            modes))
        resource-ref-replacements (zipmap current-resource-refs
                                          available-resource-refs)]
    (update schedule
            :r/modes
            make-mode-refs-consistent-impl
            resource-ref-replacements)))

(defn prune-mode-refs
  [{:as schedule, :r/keys [modes]}]
  (update schedule
          :r/modes (partial into {} (filter (comp not-empty :mode/req val)))))

(defn consistent-schedule
  [{:as schedule, :r/keys [tasks modes]}]
  (-> schedule
      make-mode-refs-consistent
      prune-mode-refs
      make-task-refs-consistent))

(defn summarise
  [{:as schedule, :r/keys [tasks modes]}]
  (let [task-ids (set (keys tasks))
        task-deps (into #{} (mapcat :deps/ids (vals tasks)))
        modes-ids (set (keys modes))
        task-modes (into #{} (mapcat :modes/ids (vals tasks)))]
    {:consistent? (and (set/superset? task-ids task-deps)
                       (set/superset? modes-ids task-modes)),
     :schedule schedule}))

(defn sched
  [size seed]
  (gen/generate (gen/fmap consistent-schedule
                          (->> (s/gen ::schedule)
                               (gen/such-that (comp not-empty :r/tasks))
                               (gen/such-that (comp not-empty :r/resources))
                               (gen/such-that (comp not-empty :r/modes))))
                size
                seed))

(defn -main
  [& args]
  (let [parsed (map #(Integer/parseInt %) args)]
    (println (json/write-value-as-string (summarise (apply sched parsed))))))

(comment
  (defn tty-tap [x] (clojure.pprint/pprint x) (println :=====================))
  (add-tap tty-tap)
  (clojure.pprint/pprint (summarise (sched 0 0)))
  (tap> (summarise (sched 0 0)))
  :.)
