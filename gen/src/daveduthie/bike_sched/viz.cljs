(ns daveduthie.bike-sched.viz
  (:require ["@antv/g6" :as G6]
            ["react-dom" :as rdom]
            [cljs-bean.core :as b]
            [clojure.core.async :as a]
            [clojure.core.async.interop :refer-macros [<p!]]
            [helix.core :refer [$ defnc]]
            [helix.dom :as d]
            [helix.hooks :as hooks]
            [react :as r]
            [goog.string :as gstr]
            goog.string.format ; leave me
            ))

(enable-console-print!)

(defn- graph-config
  [container]
  #js {:container container,
       :fitView   true,
       :width     js/document.documentElement.clientWidth,
       :height    2000,
       :layout    #js {:type          "dagre",
                       :rankdir       "LR",
                       :align         "DL",
                       :nodesep       20,
                       :ranksep       50,
                       :controlPoints true},
       :defaultEdge #js {:style #js {:lineWidth 1}}
       :modes     #js {:default #js ["drag-canvas" "drag-combo" "drag-node"]}})

(defnc setParam
  [{:keys [label value setter]}]
  (d/div
   {:style {:display "flex" :justify-content "space-between"
            :margin  "10px"
            :width   "300px"}}
   (d/label label)
   (d/input {:value value, :on-change #(setter (.. % -target -value))})))

(defnc graph
  "Container for G6"
  []
  (let [ref              (r/useRef nil)
        [size setSize]   (hooks/use-state 2)
        [seed setSeed]   (hooks/use-state 2)
        [graph setGraph] (hooks/use-state nil)
        [data setData]   (hooks/use-state #js {})]
    (hooks/use-effect
     :auto-deps
     (when-not graph
       ;; init
       (setGraph (G6/Graph. (graph-config (rdom/findDOMNode (.-current ref))))))
     ;; destroy
     #(.destroy graph)
     (when graph (.data graph data) (.render graph)))
    (d/div
     ($ setParam {:label "Size" :value size :setter setSize})
     ($ setParam {:label "Seed" :value seed :setter setSeed})
     (d/button
      {:style {:margin "10px"}
       :on-click
       (fn []
         (a/go (let [res  (<p! (js/fetch (gstr/format "/project/%d/%d/resource-contention"
                                                      size seed)))
                     data (when (.-ok res) (<p! (.json res)))]
                 (when data (prn :data! (keys (b/bean data))) (setData data)))))}
      "refresh")
     (d/div {:ref ref, :style {:border "2px solid orange"}}))))

(defnc app []
  (d/div
   (d/h1 "Welcome!")
   ;; create elements out of components
   ($ graph)
   ))

;; start your app with your favorite React renderer
(defn ^:export ^:dev/after-load init []
  (rdom/render ($ app)
               (js/document.getElementById "app")))

(comment

  (doto (G6/Graph.
         (clj->js {:container "graph",
                   :width 1200,
                   :height 800}))
    (.data graph-data)
    (.render))


  (b/bean #js {:a 1})

  (+ 1 2)

  )
