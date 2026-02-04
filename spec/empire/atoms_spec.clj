(ns empire.atoms-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.config :as config]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "set-error-message"
  (before (reset-all-atoms!))

  (it "sets the error message text"
    (atoms/set-error-message "test error" config/error-message-duration)
    (should= "test error" @atoms/error-message))

  (it "sets error-until to a future timestamp"
    (let [before (System/currentTimeMillis)]
      (atoms/set-error-message "test error" config/error-message-duration)
      (should (>= @atoms/error-until (+ before config/error-message-duration)))))

  (it "error expires after the specified duration"
    (atoms/set-error-message "vanishing error" 50)
    (should (< (System/currentTimeMillis) @atoms/error-until))
    (Thread/sleep 60)
    (should (>= (System/currentTimeMillis) @atoms/error-until))))
