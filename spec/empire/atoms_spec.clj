(ns empire.atoms-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "set-error-message"
  (before (reset-all-atoms!))

  (it "sets the error message text"
    (atoms/set-error-message "test error" 3000)
    (should= "test error" @atoms/error-message))

  (it "sets error-until to a future timestamp"
    (let [before (System/currentTimeMillis)]
      (atoms/set-error-message "test error" 3000)
      (should (>= @atoms/error-until (+ before 3000)))))

  (it "error expires after the specified duration"
    (atoms/set-error-message "vanishing error" 50)
    (should (< (System/currentTimeMillis) @atoms/error-until))
    (Thread/sleep 60)
    (should (>= (System/currentTimeMillis) @atoms/error-until))))
