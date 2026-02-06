(ns empire.save-load-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "load menu atoms"
  (before (reset-all-atoms!))

  (it "load-menu-open defaults to false"
    (should= false @atoms/load-menu-open))

  (it "load-menu-files defaults to empty vector"
    (should= [] @atoms/load-menu-files))

  (it "load-menu-hovered defaults to nil"
    (should-be-nil @atoms/load-menu-hovered)))
