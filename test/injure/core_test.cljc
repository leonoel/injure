(ns injure.core-test
  (:require #?(:clj [injure.core :refer [inject target]])
                    [clojure.test :refer [deftest is]])
  #?(:cljs
     (:require-macros
       [injure.core :refer [inject target]]
       [injure.core-test])))

(def stats-graph
  `{:n  (count (target :xs))
    :m  (/ (reduce + (target :xs)) (target :n))
    :m2 (/ (reduce + (map #(* % %) (target :xs))) (target :n))
    :v  (- (target :m2) (* (target :m) (target :m)))})

(deftest stats
  (let [xs [1 2 3 6]]
    (inject (assoc stats-graph :xs 'xs)
      (is (= (target :xs) [1 2 3 6]))
      (is (= (target :n) 4))
      (is (= (target :m) 3))
      (is (= (target :m2) (/ 25 2)))
      (is (= (target :v) (/ 7 2))))))