(ns daveduthie.bike-sched.http-server
  (:require [integrant.core :as ig]
            [org.httpkit.server :as httpkit]))

(defmethod ig/init-key ::http-server [_ {:keys [port handler]}]
  (httpkit/run-server handler {:port port :legacy-return-value? false}))

(defmethod ig/halt-key! ::http-server [_ http-server]
  (.stop http-server 100))
