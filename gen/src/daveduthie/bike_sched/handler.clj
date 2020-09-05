(ns daveduthie.bike-sched.handler
  (:require
   [reitit.ring :as ring]
   [reitit.coercion.spec]
   [reitit.ring.coercion :as rrc]
   [reitit.ring.middleware.muuntaja :as muuntaja]
   [muuntaja.core :as m]
   [daveduthie.bike-sched.gen :as gen]))

(def router
  (ring/router
   ["/project/:size/:seed"
    [[""
      {:get {:parameters {:path {:size int?, :seed int?}},
             :handler (fn [{{{:keys [size seed]} :path} :parameters}]
                        {:status 200, :body (gen/sample-project size seed)})}}]
     ["/resource-contention"
      {:get {:parameters {:path {:size int?, :seed int?}},
             :handler (fn [{{{:keys [size seed]} :path} :parameters}]
                        {:status 200,
                         :body (-> (gen/sample-project size seed)
                                   gen/resource-contention)})}}]]]
   ;; router data effecting all routes
   {:data {:muuntaja m/instance
           :coercion reitit.coercion.spec/coercion,
           :middleware [muuntaja/format-middleware
                        rrc/coerce-exceptions-middleware
                        rrc/coerce-request-middleware
                        rrc/coerce-response-middleware]}}))

(def app (ring/ring-handler router))


(comment
  (reitit.core/match-by-path router "/project/2/2/resource-contention")

  (app {:request-method :get, :uri "/project/2/2/resource-contention"}))
