(ns ^:no-doc injure.impl.core
  (:require [injure.impl.mexp :refer [macroexpand-all]]))

(def ^ThreadLocal context (ThreadLocal.))

(defn emit-target [env id]
  (if-some [ctx (.get context)]
    (if-some [bind (get (:bind ctx) id)]
      (if (contains? (:form ctx) id)
        bind (throw (ex-info "Unable to resolve cyclic dependency." {:target id})))
      (let [bind (gensym)]
        (.set context (update ctx :bind assoc id bind))
        (let [form (macroexpand-all env (apply (:repo ctx) id))]
          (.set context (-> (.get context)
                            (update :form assoc id form)
                            (update :slot conj id))) bind)))
    (throw (ex-info "Unable to find injection context." {:target id}))))

(defn emit-inject [env repo body]
  (let [prev (.get context)]
    (.set context {:repo repo :slot [] :bind {} :form {}})
    (try (let [forms (mapv (partial macroexpand-all env) body)
               {:keys [bind form slot]} (.get context)]
           `(let [~@(mapcat (juxt bind form) slot)] ~@forms))
         (finally (.set context prev)))))