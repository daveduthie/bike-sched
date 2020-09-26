(ns daveduthie.bike-sched.handler
  (:require [clojure.java.io :as io]
            [clojure.spec.alpha :as s]
            [daveduthie.bike-sched.gen :as gen]
            [daveduthie.bike-sched.graphql :as graphql]
            [integrant.core :as ig]
            [muuntaja.core :as m]
            reitit.coercion.spec
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [reitit.ring.middleware.muuntaja :as muuntaja]))

(s/def ::query string?)
(s/def ::variables (s/nilable map?))
(s/def ::operationName (s/nilable string?))
(s/def ::graphql-params
  (s/keys :req-un [::query]
          :opt-un [::variables ::operationName]))

(def routes
  [;; REST-ish routes
   ["/project/:size/:seed"
    [[""
      {:get {:parameters {:path {:size int?, :seed int?}},
             :handler    (fn [{{{:keys [size seed]} :path} :parameters}]
                           {:status 200,
                            :body   (gen/sample-project size seed)})}}]
     ["/resource-contention"
      {:get {:parameters {:path {:size int?, :seed int?}},
             :handler    (fn [{{{:keys [size seed]} :path} :parameters}]
                           {:status 200,
                            :body   (-> (gen/sample-project size seed)
                                        gen/resource-contention)})}}]]]
   ["/graphiql" (constantly
                 {:status  302
                  :headers {"Location" "/graphiql.html"}})]
   ["/graphql"
    {:post {:parameters {:body ::graphql-params}
            :handler
            (fn [{:as request
                  {{:keys [query variables operationName]} :body} :parameters}]
              {:status 200
               :body   (graphql/execute request query variables operationName)})}}]])

(def handler
  (ring/ring-handler
   (ring/router routes
                {:data {:muuntaja   m/instance,
                        :coercion   reitit.coercion.spec/coercion,
                        :middleware [muuntaja/format-middleware
                                     rrc/coerce-exceptions-middleware
                                     rrc/coerce-request-middleware
                                     rrc/coerce-response-middleware]}})
   (ring/routes (ring/create-resource-handler {:path "/", :root "public"})
                (ring/create-default-handler))))

(comment
  (require '[integrant.repl.state :as irs])

  (-> (:daveduthie.bike-sched.handler/handler irs/system)
      (apply [{:request-method :get :uri "/css/styles.css"}])
      )

  (require '[clojure.java.browse :as browse])
  (browse/browse-url "http://localhost:8080/graphiql")

  )
