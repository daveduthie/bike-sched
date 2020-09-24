(ns daveduthie.bike-sched.system
  (:require [integrant.core :as ig]
            [daveduthie.bike-sched.handler :as handler]
            [daveduthie.bike-sched.http-server :as http-server]
            [daveduthie.bike-sched.shadow-cljs-server :as shadow-cljs-server]))

(def system
  {::http-server/http-server   {:port 8080, :handler (ig/ref ::handler/handler)}
   ::handler/handler           {}
   ::shadow-cljs-server/server {:builds
                                {:app
                                 {:target     :browser
                                  :devtools   {:preloads ['devtools.preload
                                                          'shadow.remote.runtime.cljs.browser]}
                                  :output-dir "resources/public/js"
                                  :asset-path "/js"
                                  :modules    {:main {:entries ['daveduthie.bike-sched.viz]}}
                                  :dev        {:compiler-options {:output-feature-set :es6}}}}}})

(defn -main [& args]
  (ig/init system))

(comment
  (require '[integrant.repl.state :as irs])

  (-> (:duct.server/shadow-cljs irs/system))

  )
