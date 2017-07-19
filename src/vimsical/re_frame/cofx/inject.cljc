(ns vimsical.re-frame.cofx.inject
  "Inject a subscription's value in an event-handlers coeffects map."
  (:require
   [re-frame.core :as re-frame]
   [re-frame.interop :as re-frame.interop]))

(defn- inject-sub-ignore-dispose-warnings?
  [query-vector]
  (:ignore-warnings (meta query-vector)))

(defn dispose-maybe
  "Dispose of `ratom-or-reaction` if it has no watches."
  [query-vector ratom-or-reaction]
  ;; Behavior notes:
  ;;
  ;; 1. calling `reagent/dispose!` takes care of "walking up" the reaction graph
  ;;    to the nodes that the reaction was `watching` and remove itself from
  ;;    that node's `watches`'.
  ;;
  ;; 2. In turn removing a watch causes that node to dispose of itself iff it
  ;;    has no other watches.
  ;;
  ;; 3. Essentially disposing of a node will dispose of all its dependencies iff
  ;;    they're not needed by another node.
  ;;
  ;; 4. Re-frame adds an on-dispose hook to the reactions returned by
  ;;    `re-frame/subscribe` that will dissoc them from the subscription cache.
  ;;
  ;; There are some potential issues with this scheme:
  ;;
  ;; Imagine a sub that is only used by `inject-sub` and not by components, then
  ;; it will never have watches. This seems like it would be a problem if that
  ;; sub now gets injected in multiple handlers. Due to the disposal behavior
  ;; descibed above, for every handler the cofx would cause:
  ;;
  ;; - subscribe to find the cache empty
  ;; - subscribe to grab the event handler and produce a value
  ;; - subscribe to wrap the value in a reaction
  ;; - the cofx to deref that reaction
  ;; - the cofx to dispose of it (it has no watches)
  ;; - the reaction to remove itself from the cache (on-dispose hook)
  ;;
  ;; We'd basically pay for a cache miss every time we inject that sub?
  ;;
  #?(:cljs
     (when-not (seq (.-watches ratom-or-reaction))
       (when-not (inject-sub-ignore-dispose-warnings? query-vector)
         (console :warn "Disposing of injected sub:" query-vector))
       (re-frame.interop/dispose! ratom-or-reaction))))

(defmulti inject-sub-cofx
  (fn [coeffects query-vector-or-event->query-vector-fn]
    (if (vector? query-vector-or-event->query-vector-fn)
      ::query-vector
      ::event->query-vector-fn)))

(defmethod inject-sub-cofx ::query-vector
  [coeffects [id :as query-vector]]
  (let [sub (re-frame/subscribe query-vector)
        val (deref sub)]
    (dispose-maybe query-vector sub)
    (assoc coeffects id val)))

(defmethod inject-sub-cofx ::event->query-vector-fn
  [{:keys [event] :as coeffects} event->query-vector-fn]
  (if-some [[id :as query-vector] (event->query-vector-fn event)]
    (let [sub (re-frame/subscribe query-vector)
          val (deref sub)]
      (dispose-maybe query-vector sub)
      (assoc coeffects id val))
    coeffects))

(defn inject-sub
  "Inject the `:sub` cofx.

  If `query-vector-or-event->query-vector-fn` is a query vector, subscribe and
  dereference that subscription before assoc'ing its value in the coeffects map
  under the id of the subscription and disposing of it.

  If `query-vector-or-event->query-vector-fn` is a fn, it should take a single
  argument which is the event parameters vector for that handler (similar to the
  2-arity of `re-frame.core/reg-sub`). Its return value should be a query-vector
  or nil. From there on the behavior is similar to when passing a query-vector.

  NOTE that if there are no components subscribed to that subscription the cofx
  will dispose of it in order to prevent leaks. However there is a performance
  penalty to doing this since we pay for a re-frame subscription cache miss
  every time we inject it. In such cases the cofx will log a warning which can
  be ignored by setting `:ignore-warnings` on the query vector's meta. A rule of
  thumb for what to do here would be that if an injected sub is disposed of very
  often, we should either rework the subscription graph so that it ends up used
  by a component and thus cached, or we should extract the db lookup logic into
  a function that can be used to get the value straight from the db inside the
  handler. It seems safe to decide to ignore the warning when the disposal
  doesn't happen too often and it is just more convenient to reuse the
  subscription's logic.

  Examples:

  ;; Query vector:

  (re-frame/reg-sub ::injected-static ...)
  (re-frame/reg-event-fx
   ::handler
   [(inject-sub ^:ignore-warnings [::injected-static]]]
   (fn [{:as cofx {::keys [injected-static]} params]
     ...)

  ;; Fn of event to query vector:

  (re-frame/reg-sub ::injected-dynamic (fn [_ [_ arg1 arg2]] ...))
  (re-frame/reg-event-fx
   ::handler
   [(inject-sub
      (fn [[_ event-arg1 event-arg2]]
        ...
        ^:ignore-warnings [::injected-dynamic arg1 arg2]))]
   (fn [{:as cofx {::keys [injected-dynamic]} [_ event-arg1 event-arg-2]]
     ...)
  "
  [query-vector-or-event->query-vector-fn]
  {:pre [(or (vector? query-vector-or-event->query-vector-fn)
             (ifn? query-vector-or-event->query-vector-fn))]}
  (re-frame/inject-cofx
   :sub
   query-vector-or-event->query-vector-fn))

;;
;; * Entry point
;;

(defn reg-cofx
  ([] (reg-cofx :inject))
  ([cofx] (re-frame/reg-cofx cofx inject-sub-cofx)))
