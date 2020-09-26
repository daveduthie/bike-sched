(ns user
  (:require [daveduthie.bike-sched.system :as system]
            [integrant.repl :refer [go halt reset reset-all]]))

(integrant.repl/set-prep! (fn [] @#'system/system))

(comment
  (go)
  (halt)
  (reset)
  (reset-all)
  )
