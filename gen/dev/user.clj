(ns user
  (:require [integrant.repl :refer [clear go halt prep init reset reset-all]]))

(comment
  (integrant.repl/set-prep!
   (constantly @(requiring-resolve 'daveduthie.bike-sched.system/system))))

(comment
  (go)
  (halt)
  (reset)
  )
