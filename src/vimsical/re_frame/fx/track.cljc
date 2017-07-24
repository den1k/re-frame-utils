(ns vimsical.re-frame.fx.track
  "Dispatch events when subscriptions change.

  Tracking a subscription is a stateful process that needs to be registered and
  disposed of using an id.

  Fx input map:

  `:action` either `:register` or `:dispose`
  `:id` any value
  `:subscription` a subscription vector
  `:event-fn` a fn of a subscription value to an event vector.

  Once a track for a subscription is registered, every time that subscription
  updates the track will invoke `:event-fn` with the subscription's value, it
  should return an event vector for `re-frame.core/dispatch` or nil for a no-op.

  Usage:

  (require '[vimsical.re-frame.fx.track :as track])

  0. Context

    Given the following subscription:

    (re-frame/reg-sub ::my-sub (fn [_ sub-arg] ...))

    We want to invoke a handler for every update

    (re-frame/reg-event ::my-sub-did-update (fn [_ sub-arg sub-value] ...))

  1. Register a track

    (re-frame/reg-event-fx
      ::register-track
      (fn [cofx event]
       {::track/register
        {:id           :my-track
         :subscription [::my-sub \"my-sub-arg\"]
         :event-fn     (fn [sub-value] [::my-sub-did-update \"my-sub-arg\" sub-value])}}))

  2. Dispose of the track using its id

    (re-frame/reg-event-fx
      ::dispose-track
      (fn [cofx event]
       {::track/dispose
        {:id :my-track}}))
  "
  (:require
   [re-frame.core :as re-frame]
   [reagent.ratom :as ratom]))


;;
;; * Register
;;


(defonce ^:private register (atom {}))


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


(defn ensure-vec [x] (if (sequential? x) x [x]))


;;
;; * Fx handlers
;;


(defn register-fx
  [track-or-tracks]
  (doseq [{:keys [id] :as track} (ensure-vec track-or-tracks)]
    (if-some [track' (get @register id)]
      (throw (ex-info "Track already exists" {:track track' :tried track}))
      (let [track (new-reagent-track track)]
        (swap! register assoc id track)))))


(defn dispose-fx
  [track-or-tracks]
  (doseq [{:keys [id] :as track} (ensure-vec track-or-tracks)]
    (if-some [track (get @register id)]
      #?(:cljs (do (ratom/dispose! track) (swap! register dissoc id)) :clj nil)
      (throw (ex-info "Track isn't registered" {:track track})))))


;;
;; * Entry point
;;

(re-frame/reg-fx ::register register-fx)
(re-frame/reg-fx ::dispose dispose-fx)
