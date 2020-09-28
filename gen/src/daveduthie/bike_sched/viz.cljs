(ns daveduthie.bike-sched.viz
  (:require
   ["@antv/g2plot" :as g2plot]
   ["@antv/graphin" :as Graphin :default Graphin]
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
  (let [[size setSize]     (hooks/use-state 2)
        [seed setSeed]     (hooks/use-state 2)
        [layout setLayout] (hooks/use-state "dagre")
        [data setData]     (hooks/use-state #js {})
        layout-options     [
                            {:value "dagre" :label "Dagre"}
                            {:value "circle" :label "Circle"}
                            {:value "concentric", :label "Concentric"}
                            {:value "force" :label "Force"}
                            {:value "grid" :label "Grid"}
                            {:value "radial" :label "Radial"}
                            {:value "random", :label "Random"}
                            {:value "tweak", :label "Tweak"}
                            ]]
    (d/div
     ($ setParam {:label "Size" :value size :setter setSize})
     ($ setParam {:label "Seed" :value seed :setter setSeed})
     (d/label "Layout")
     (d/select {:on-change (fn [e] (setLayout (-> e .-target .-value)))}
               (for [o layout-options]
                 (d/option {:key o :& o})))
     (d/button
      {:style {:margin "10px"}
       :on-click
       (fn []
         (a/go
           (let [res  (<p! (js/fetch
                            (gstr/format "/project/%d/%d"
                                         size seed)))
                 data (when (.-ok res) (<p! (.json res)))]
             (when data (prn :data! (keys (b/bean data))) (setData data)))))}
      "Project")
     (d/button
      {:style {:margin "10px"}
       :on-click
       (fn []
         (a/go
           (let [res  (<p! (js/fetch
                            (gstr/format "/project/%d/%d/resource-contention"
                                         size seed)))
                 data (when (.-ok res) (<p! (.json res)))]
             (when data (prn :data! (keys (b/bean data))) (setData data)))))}
      "Resource contention")
     (d/div {:style {:height "80vh"
                     :border "1px solid gray"}}
            ($ Graphin {:data   data
                        ;; :style  {:height "100vh"}
                        :layout #js {:name layout}})))))

(def example-bar1
  ;; TODO(daveduthie): This illustrates an inefficient schedule. Task
  ;; 2 is completed at time 30, but tasks 3 and 4 are each delayed by
  ;; 10 units.
  (->> [{:task "one", :type "Stitch 1", :values [0 20]}
        {:task "two", :type "Stitch 1", :values [20 30]}
        {:task "one", :type "Stitch 2", :values [0 20]}
        {:task "two", :type "Stitch 3", :values [0 30]}
        {:task "three", :type "Stitch 1", :values [30 40]}
        {:task "three", :type "Stitch 2", :values [30 40]}
        {:task "four", :type "Stitch 1", :values [40 50]}
        {:task "four", :type "Stitch 2", :values [40 50]}
        ]
       (sort-by :values)
       ))

(def example-bar2
  "This one is better"
  (->> [{:task "one", :type "Stitch 1", :values [0 20]}
        {:task "one", :type "Stitch 2", :values [0 20]}
        {:task "two", :type "Stitch 3", :values [0 40]}
        {:task "three", :type "Stitch 1", :values [20 30]}
        {:task "three", :type "Stitch 2", :values [20 30]}
        {:task "four", :type "Stitch 1", :values [30 40]}
        {:task "four", :type "Stitch 2", :values [30 40]}
        ]
       (sort-by :values)
       ))

(defnc otherGraph
  "Static bar chart"
  []
  (let [[data setData] (hooks/use-state example-bar1)
        chart-ref      (hooks/use-ref nil)
        data-o         {"one" (b/->js example-bar1)
                        "two" (b/->js example-bar2)}
        data-options   [{:label "One" :value "one"}
                        {:label "Two" :value "two"}]]
    (hooks/use-effect
     :auto-deps
     (let [options (b/->js
                    {:data        data
                     :xField      "values"
                     :yField      "type"
                     :seriesField "task"
                     :isRange     true})]
       (if @chart-ref
         (do (prn :update)
             (.update @chart-ref options)
             (.render @chart-ref))
         (do (prn :create)
             (let [instance (g2plot/Bar. "bar-chart" options)]
               (.render instance)
               (reset! chart-ref instance))))))
    (d/div
     (d/label "Data")
     (d/select {:on-change #(setData (-> % .-target .-value data-o))}
               (for [o data-options]
                 (d/option {:key o :& o})))
     (d/div {:id    "bar-chart"
             :style {:height "50vh"}}))))

(comment
  (tap> (b/bean ant-charts))
  (tap> ::ok)
  Bar
  (b/bean ant-charts)
  (keys (b/bean ant-charts))
  antCharts)

(comment
  (b/->clj (-> Graphin/Utils (.mock 5) .tree .graphin)))

(defnc app []
  (d/div
   (d/h1 "Welcome!")
   ($ otherGraph)
   ($ graph)))

(defn ^:export ^:dev/after-load init []
  (rdom/render ($ app) (js/document.getElementById "app")))

(init)

(comment


  (tap> ::foo)
  (tap> (b/bean (js/document.getElementById "app")))

  )
