(ns user
  (:require [integrant.repl :refer [clear go halt prep init reset reset-all]]
            [daveduthie.bike-sched.system :as system]))

(integrant.repl/set-prep! (fn [] system/system))

(comment
  (go)
  (halt)
  (reset)
  )
