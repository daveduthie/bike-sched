(ns daveduthie.plot
  (:require
   [clj-polyglot.core :as poly]
   [clj-polyglot.js :as poly.js]))

(def asciichart-src
  (slurp "https://cdn.jsdelivr.net/npm/asciichart@1.5.21/asciichart.js"))

(def ctx (poly.js/js-ctx asciichart-src))

(def asciichart (poly.js/from ctx "asciichart"))

(def api (poly.js/import asciichart [:plot]))

(defn plot [values] (poly/eval api :plot values))
