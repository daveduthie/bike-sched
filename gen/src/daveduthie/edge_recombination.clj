(ns daveduthie.edge-recombination
  (:require [clojure.set :as set]
            [clojure.test :as test]
            [clojure.test.check :as check]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.clojure-test :as check-test]
            [clojure.test.check.properties :as check-prop]))

(defn adjacencies
  [xs]
  (let [c (count xs)]
    (into {}
          (for [i (range c)]
            (let [prev (case i
                         0 (dec c)
                         (dec i))
                  nxt  (if (= c (inc i)) 0 (inc i))]
              [(nth xs i) (hash-set (nth xs prev) (nth xs nxt))])))))

(rand-nth (seq (adjacencies '[a b c d e f g])))

(defn edge-recombination
  [a b]
  ;; make seedable?
  (let [parent-count (count a)]
    (loop [adjacency (merge-with set/union (adjacencies a) (adjacencies b))
           K         []
           N         (rand-nth [(first a) (first b)])]
      (if (= parent-count (count K))
        K
        (let [K            (conj K N)
              adjacency    (into {} (map (fn [[k v]] [k (disj v N)])) adjacency)
              N-neighbours (->> (adjacency N)
                                (map (partial find adjacency))
                                (sort-by (comp count second)))
              nxt          (if (not-empty N-neighbours)
                             (ffirst N-neighbours)
                             (let [in-K (set K)]
                               (first (remove in-K (keys adjacency)))))]
          (recur adjacency K nxt))))))

(edge-recombination '[a b c d e] '[e d a c b])

(def alpha-syms (mapv (comp symbol str char) (range 65 91)))

(def the-prop
  (check-prop/for-all [a (gen/shuffle alpha-syms) b (gen/shuffle alpha-syms)]
                      (let [recombined (edge-recombination a b)]
                        (test/is (= (count a) (count b) (count recombined)))
                        (test/is (= (set a) (set b) (set recombined))))))

(check-test/defspec abcdefg 100 the-prop)

(comment (check/quick-check 100 the-prop)
         (test/run-tests))
