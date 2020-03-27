(ns ^:no-doc injure.impl.mexp
  (:require [cljs.analyzer :as a]
            [cljs.analyzer.api :as aa]
            [clojure.tools.analyzer.jvm :as jvm])
  (:import (clojure.lang Compiler$LocalBinding)))

(def macroexpand-all
  (letfn [(emit-method [{:keys [variadic? params body]}]
            (list (if variadic?
                    (-> (into [] (map :name) (pop params))
                        (conj '& (-> params peek :name)))
                    (into [] (map :name) params)) (emit-ast body)))
          (emit-ast [ast]
            (case (:op ast)
              :let
              (list 'let (into [] (mapcat
                                    (fn [{:keys [name init]}]
                                      [name (emit-ast init)]))
                               (:bindings ast))
                    (emit-ast (:body ast)))

              :loop
              (list 'loop (into [] (mapcat
                                     (fn [{:keys [name init]}]
                                       [name (emit-ast init)]))
                                (:bindings ast))
                    (emit-ast (:body ast)))

              :recur
              (cons 'recur (map emit-ast (:exprs ast)))

              :invoke
              (map emit-ast (cons (:fn ast) (:args ast)))

              :fn
              (cons 'fn (concat (when-some [l (:local ast)] [(:name l)])
                                (map emit-method (:methods ast))))

              :letfn
              (list 'letfn (into [] (map (fn [{:keys [name init]}]
                                           (cons name (map emit-method (:methods init)))))
                                 (:bindings ast))
                    (emit-ast (:body ast)))

              :try
              (list* 'try (emit-ast (:body ast))
                     (list 'catch :default (:name ast) (emit-ast (:catch ast)))
                     (when-some [f (:finally ast)]
                       [(list 'finally (emit-ast f))]))

              :throw
              (list 'throw (emit-ast (:exception ast)))

              :new
              (cons 'new (map emit-ast (cons (:class ast) (:args ast))))

              :def
              (list 'def (emit-ast (:var ast)) (emit-ast (:init ast)))

              :set!
              (list 'set! (emit-ast (:target ast)) (emit-ast (:val ast)))

              :js
              (list* 'js* (or (:code ast) (apply str (interpose "~{}" (:segs ast)))) (map emit-ast (:args ast)))

              :do
              (cons 'do (conj (mapv emit-ast (:statements ast)) (emit-ast (:ret ast))))

              :map
              (zipmap (map emit-ast (:keys ast)) (map emit-ast (:vals ast)))

              :set
              (into #{} (map emit-ast) (:items ast))

              :vec
              (into [] (map emit-ast) (:items ast))

              :if
              (list 'if (emit-ast (:test ast)) (emit-ast (:then ast)) (emit-ast (:else ast)))

              :case
              (list* 'case (emit-ast (:test ast))
                     (-> []
                         (into (mapcat (fn [{:keys [tests then]}]
                                         [(map :form tests) (emit-ast (:then then))]))
                               (:nodes ast))
                         (conj (emit-ast (:default ast)))))

              :host-field
              (list '. (emit-ast (:target ast)) (symbol (str "-" (name (:field ast)))))

              (:form ast)))
          (coerce-local [[symbol binding]]
            [symbol (or (when (instance? Compiler$LocalBinding binding)
                          (let [binding ^Compiler$LocalBinding binding]
                            {:op   :local
                             :tag  (when (.hasJavaClass binding)
                                     (some-> binding (.getJavaClass)))
                             :form symbol
                             :name symbol})) binding)])]
    (fn [env form]
      (if (:js-globals env)
        (emit-ast (binding [a/*cljs-warnings* (assoc a/*cljs-warnings* :undeclared-var false)]
                    (aa/analyze env form)))
        (->> env
             (into {} (map coerce-local))
             (update (jvm/empty-env) :locals merge)
             (jvm/macroexpand-all form))))))