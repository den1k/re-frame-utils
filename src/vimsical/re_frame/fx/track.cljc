(ns vimsical.re-frame.fx.track
  "Dynamically dispatch events when subscriptions update.

  Usage:

  Invoke `reg-fx` to register the fx handler.

  Refer to the docstring for `track`.
  "
  (:require
   [re-frame.core :as re-frame]
   [reagent.ratom :as ratom]))


;;
;; * Register
;;


(defonce ^:private track-register (atom {}))


;;
;; * Internal helpers
;;


(defn- new-reagent-track
  "Create a new reagent track that will execute every time `subscription`
  updates.

  `event-fn` is invoked with the subscription value, it should return an event
  vector for `re-frame.core/dispatch`, or nil for a no-op.

  If `dispatch-first?` is true, dereference and dispatch right away using the
  current value, if false dispatch will happen on the next update. Defaults to
  true.
  "
  [{:keys [subscription event-fn dispatch-first?] :or {dispatch-first? true}}]
  {:pre [(vector? subscription) (ifn? event-fn)]}
  #?(:cljs
     (let [dispatched-first? (atom false)]
       (ratom/track!
        (fn []
          (let [sub-value @(re-frame/subscribe subscription)]
            (when-some [event-vector (event-fn sub-value)]
              (when (or dispatch-first?
                        @dispatched-first?
                        (do (reset! dispatched-first? true) nil))
                (re-frame/dispatch event-vector)))))))))


;;
;; * Fx handler
;;


(defmulti track-fx* :action)


(defmethod track-fx* :register
  [{:keys [id] :as track}]
  (if-some [track' (get @track-register id)]
    (throw (ex-info "Track already exists" {:track track' :tried track}))
    (let [track (new-reagent-track track)]
      (swap! track-register assoc id track))))


(defmethod track-fx* :dispose
  [{:keys [id] :as track}]
  (if-some [track (get @track-register id)]
    #?(:cljs (do (ratom/dispose! track) (swap! track-register dissoc id)) :clj nil)
    (throw (ex-info "Track isn't registered" {:track track}))))


(defn track
  "Dispatch an event when a subscription changes.

  Tracking a subscription is a stateful process that needs to be registered and
  disposed of using an id.

  Fx input map:

  `:action` either `:register` or `:dispose`
  `:id` any value
  `:subscription` a subscription vector
  `:val->event` a fn of a subscription value to an event vector.

  Once a track for a subscription is registered, every time that subscription
  updates the track will invoke `:val->event` with the subscription's value, it
  should return an event vector for `re-frame.core/dispatch` or nil for a no-op.

  Usage:

  0. Context

    Given the following subscription:

    (re-frame/reg-sub ::my-sub (fn [_ arg] ...))

    We want to invoke a handler with every update

    (re-frame/reg-event ::my-sub-updated (fn [_ arg sub-value] ...))

  1. Register a track

    (re-frame/reg-event-fx
      ::start-track
      (fn [cofx event]
       {:track
        {:action       :register
         :id           :my-track
         :subscription [::my-sub \"my-arg\"]
         :val->event   (fn [sub-value] [::my-sub-updated \"my-arg\" sub-value])}}))

  2. Dispose of the track using its id

    (re-frame/reg-event-fx
      ::stop-track
      (fn [cofx event]
       {:track
        {:action :dispose
         :id     :my-track}}))
  "
  [track-or-tracks]
  (letfn [(ensure-vec [x]
            (if (sequential? x) (vec x) (vector x)))]
    (doseq [track (ensure-vec track-or-tracks)]
      (track-fx* track))))

;;
;; * Entry point
;;

(defn reg-fx
  ([] (reg-fx :track))
  ([fx] (re-frame/reg-fx fx track)))
