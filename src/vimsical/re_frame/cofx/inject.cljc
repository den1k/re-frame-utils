(ns vimsical.re-frame.cofx.inject
  "Inject a subscription in an event handler.

  *** Performance caveat ***

  Do not inject subscriptions unless they are also used in a component.

  For more details refer to the docstring for `inject` and for some context
  https://github.com/Day8/re-frame/issues/255#issuecomment-299318797
  "
  (:require
   [re-frame.core :as re-frame]
   [re-frame.loggers :refer [console]]
   [re-frame.interop :as re-frame.interop]))


(defn- ignore-dispose?
  [query-vector]
  (:ignore-dispose (meta query-vector)))


(defn- dispose-maybe
  "Dispose of `ratom-or-reaction` iff it has no watches."
  [query-vector ratom-or-reaction]
  #?(:cljs
     (when-not (seq (.-watches ratom-or-reaction))
       (when-not (ignore-dispose? query-vector)
         (console :warn "Disposing of injected subscription:" query-vector))
       (re-frame.interop/dispose! ratom-or-reaction))))


(defmulti ^:private inject
  "Inject the `:sub` cofx.

  If `query-vector-or-event->query-vector-fn` is a subscription vector, subscribe and
  dereference that subscription before assoc'ing its value in the coeffects map
  under the id of the subscription and disposing of it.

  If `query-vector-or-event->query-vector-fn` is a fn, it should take a single
  argument which is the event args vector for that handler (similar to the
  2-arity of `re-frame.core/reg-sub`). Its return value should be a query-vector
  or nil. From there on the behavior is similar to when passing a query-vector.

  NOTE that if there are no components subscribed to that subscription the cofx
  will dispose of it in order to prevent leaks. However there is a performance
  penalty to doing this since we pay for a re-frame subscription cache miss
  every time we inject it. In such cases the cofx will log a warning which can
  be ignored by setting `:ignore-dispose` on the subscription vector's meta. A
  rule of thumb for what to do here would be that if an injected sub is disposed
  of very often, we should either rework the subscription graph so that it ends
  up used by a component and thus cached, or we should extract the db lookup
  logic into a function that can be used to get the value straight from the db
  inside the handler. It seems safe to decide to ignore the warning when the
  disposal doesn't happen too often and it is just more convenient to reuse the
  subscription's logic.

  Examples:

  (require '[vimsical.re-frame.cofx.inject :as inject])

  ;; Injecting a simple subscription:

  (re-frame/reg-sub ::simple ...)

  (re-frame/reg-event-fx
   ::simple-handler
   [(re-frame/inject-cofx ::inject/sub [::simple])]
   (fn [{:as cofx ::keys [simple]} params]
     ...)


  ;; Injecting a dynamic subscription depending on the event parameters:

  (re-frame/reg-sub ::dynamic (fn [_ [_ arg1 arg2]] ...))

  (re-frame/reg-event-fx
   ::dynamic-handler
   [(re-frame/inject-cofx ::inject/sub
      (fn [[_ arg1 arg2]]
        ...
        [::dynamic arg1 arg2]))]
   (fn [{:as cofx ::keys [dynamic]} [_ arg1 arg-2]]
     ...)
  "
  (fn [coeffects query-vector-or-event->query-vector-fn]
    (cond
      (vector? query-vector-or-event->query-vector-fn) ::query-vector
      (ifn? query-vector-or-event->query-vector-fn)    ::event->query-vector-fn)))


(defmethod inject ::query-vector
  [coeffects [id :as query-vector]]
  (let [sub (re-frame/subscribe query-vector)
        val (deref sub)]
    (dispose-maybe query-vector sub)
    (assoc coeffects id val)))


(defmethod inject ::event->query-vector-fn
  [{:keys [event] :as coeffects} event->query-vector-fn]
  (if-some [[id :as query-vector] (event->query-vector-fn event)]
    (let [sub (re-frame/subscribe query-vector)
          val (deref sub)]
      (dispose-maybe query-vector sub)
      (assoc coeffects id val))
    coeffects))

;;
;; * Entry point
;;

(re-frame/reg-cofx ::sub inject)
