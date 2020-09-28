(ns daveduthie.bike-sched.system
  (:require [integrant.core :as ig]
            [daveduthie.bike-sched.handler :as handler]
            [org.httpkit.server :as httpkit]
            [shadow.cljs.devtools.config :as shadow.config]
            [shadow.cljs.devtools.server :as shadow.server]
            [shadow.cljs.devtools.api :as shadow.api]))

;; -----------------------------------------------------------------------------
;; HTTP Handler

(defmethod ig/init-key ::handler [_ _] handler/handler)

;; -----------------------------------------------------------------------------
;; HTTP server

(defmethod ig/init-key ::http-server [_ {:keys [port handler]}]
  (httpkit/run-server handler {:port port :legacy-return-value? false}))

(defmethod ig/halt-key! ::http-server [_ http-server]
  (.stop http-server 100))

;; -----------------------------------------------------------------------------
;; Shadow CLJS server

(defn- watch-options-for-build
  "Return the options for the watcher for a specific build configuration."
  [build-conf]
  (select-keys (get-in build-conf [:devtools :watch-options]) [:autobuild :verbose :sync]))

(defmethod ig/init-key ::shadow-cljs-server
  [_ conf]
  (let [conf (-> {:cache-root ".shadow-cljs"
                  :nrepl      false}
                 (merge conf)
                 shadow.config/normalize)]
    (shadow.server/start! conf)
    (doseq [build-conf (-> conf :builds vals)]
      (if (shadow.api/worker-running? (:build-id build-conf))
        :running
        (do (shadow.api/watch* build-conf (watch-options-for-build build-conf))
            :watching)))
    conf))

(defmethod ig/halt-key! ::shadow-cljs-server
  [_ _]
  (shadow.server/stop!))

(defmethod ig/suspend-key! ::shadow-cljs-server
  [_ _])

(defmethod ig/resume-key ::shadow-cljs-server
  [k new-shadow-conf old-shadow-conf old-impl]
  (when-not (= (dissoc old-shadow-conf :logger :duct.core/requires)
               (dissoc new-shadow-conf :logger :duct.core/requires))
    (ig/halt-key! k old-impl)
    (ig/init-key k new-shadow-conf)))

;; -----------------------------------------------------------------------------
;; Shadow CLJS compiler

(defn- release-options-for-build
  [build-conf]
  (-> build-conf
      :compiler-options
      (select-keys [:pseudo-names :pretty-print :debug :source-maps])))

(defn- release-build
  [build-conf opts]
  (shadow.api/release*
   build-conf
   (-> build-conf release-options-for-build (merge opts))))

(defmethod ig/init-key ::shadow-cljs-compiler
  [_ {:keys [logger] :as conf}]
  (let [conf (shadow.config/normalize conf)]
    (shadow.api/with-runtime
      (doseq [build-conf (-> conf :builds vals)]
        (release-build build-conf {:logger logger}))
      :done)))

;; -----------------------------------------------------------------------------
;; System

(def cljs-config
  {:nrepl {:port 1111}
   :builds
   {:app
    {:target     :browser
     :devtools   {:preloads ['devtools.preload
                             'shadow.remote.runtime.cljs.browser]}
     :output-dir "resources/public/js"
     :asset-path "/js"
     :modules    {:main {:entries ['daveduthie.bike-sched.viz]}}
     :dev        {:compiler-options {:output-feature-set :es2018}}
     }}})

(def system
  {::http-server          {:port 8080, :handler (ig/ref ::handler)}
   ::handler              {}
   ::shadow-cljs-server   cljs-config
   ;; ::shadow-cljs-compiler cljs-config
   })

(defn -main [& args]
  (ig/init system))

(comment
  (require '[integrant.repl.state :as irs])

  (-> (:duct.server/shadow-cljs irs/system))

  (require '[shadow.cljs.devtools.api :as shadow.api])

  (shadow.api/repl :app)

  )
