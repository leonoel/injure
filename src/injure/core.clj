(ns injure.core
  (:require [injure.impl.core :as i]))

(defmacro target
  "Requires the dependency identified by provided forms. Must be called inside an injection context."
  [& id] (i/emit-target &env id))

(defmacro inject
  "Evaluates body forms (in an implicit do), recursively resolving dependencies with the repo function."
  [repo & body] (i/emit-inject &env (eval repo) body))