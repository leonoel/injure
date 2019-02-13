# injure

A dependency injector for Clojure and ClojureScript.

[![clojars](https://img.shields.io/clojars/v/injure.svg)](https://clojars.org/injure)

[![cljdoc](https://cljdoc.org/badge/injure/injure)](https://cljdoc.org/d/injure/injure/CURRENT)

[![build](https://travis-ci.org/leonoel/injure.svg?branch=master)](https://travis-ci.org/leonoel/injure)

[![license](https://img.shields.io/github/license/leonoel/injure.svg)](LICENSE)


## Rationale

Dependency injection refers to the process of deferring bindings between components in a system. Exhibiting dependencies allows to reify the system topology as plain data, loosening coupling and increasing modularity.

`injure` aims to capture the essence of this pattern, providing helpers to describe component systems as data and arbitrarily resolve them later. Lifecycle management and other OO-related details are outside of the scope of this library.

Dependency resolution happens at compile-time and produces plain local bindings, avoiding hashmap lookups. Therefore, the resulting system has zero run-time overhead and will perform as fast as a hardwired one.


## Usage
`injure` exposes a single namespace `injure.core` with 2 macros, `target` and `inject`.

```clojure
(require '[injure.core :as i])
```

`target` requires a dependency. It takes an arbitrary number of compile-time forms and emits a symbol bound to the resolution of the dependency matched by provided forms. It is illegal to call `target` outside of an `inject` context, but resolution can be deferred with quoting.

```clojure
(def system        ;; defines a system of 3 components identified by keywords :a, :b, :c
  `{:a 6
    :b (inc (i/target :a))
    :c (* (i/target :a) (i/target :b))})
```

`inject` performs the dependency resolution of a fully defined system. The first argument must evaluate (compile-time) to a function providing forms associated to required targets (arguments to `target` will be passed as-is). Next arguments are forms to be evaluated within this context.

```clojure
(i/inject system (str (i/target :c)))     ;; emits (let [a 6, b (inc a), c (* a b)] (str c))
```


## Guides

The following patterns will be illustrated with an example taken from [plumatic/plumbing](https://github.com/plumatic/plumbing), a library providing a similar feature (with a different strategy, however).

First, let's define `stats-graph`, a partial dependency graph defining a bunch of statistics operations that can be performed on an input sequence of numeric values.

```clojure
(def stats-graph
  `{:n  (count (i/target :xs))
    :m  (/ (reduce + (i/target :xs)) (i/target :n))
    :m2 (/ (reduce + (map #(* % %) (i/target :xs))) (i/target :n))
    :v  (- (i/target :m2) (* (i/target :m) (i/target :m)))})
```


### Step optimization
We can abstract away input to build a plain function performing the minimal subset of required computations :
```clojure
(defn mean [xs]                             ;; computes :n and :m
  (i/inject (assoc stats-graph :xs 'xs)
    (i/target :m)))

(defn mean-and-variance [xs]                ;; computes :n :m :m2 and :v
  (i/inject (assoc stats-graph :xs 'xs)
    [(i/target :m) (i/target :v)]))

(mean [1 2 3 6])                  ;; returns 3
(mean-and-variance [1 2 3 6])     ;; returns [3 7/2]
```


### Unit testing
We can unit test each computation step by binding inputs to static values and validating results according to these inputs :

```clojure
(deftest unit
  (i/inject (assoc stats-graph :xs [1 2 3 6])
    (is (= (i/target :xs) [1 2 3 6]))
    (is (= (i/target :n) 4))
    (is (= (i/target :m) 3))
    (is (= (i/target :m2) (/ 25 2)))
    (is (= (i/target :v) (/ 7 2)))))
```
