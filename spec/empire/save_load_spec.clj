(ns empire.save-load-spec
  (:require [speclj.core :refer :all]
            [empire.atoms :as atoms]
            [empire.save-load :as save-load]
            [empire.test-utils :refer [reset-all-atoms!]]))

(describe "load menu atoms"
  (before (reset-all-atoms!))

  (it "load-menu-open defaults to false"
    (should= false @atoms/load-menu-open))

  (it "load-menu-files defaults to empty vector"
    (should= [] @atoms/load-menu-files))

  (it "load-menu-hovered defaults to nil"
    (should-be-nil @atoms/load-menu-hovered)))

(describe "saveable-atoms"
  (it "contains game-map"
    (should-contain :game-map save-load/saveable-atoms))

  (it "contains production"
    (should-contain :production save-load/saveable-atoms))

  (it "contains round-number"
    (should-contain :round-number save-load/saveable-atoms))

  (it "does not contain transient atoms like last-key"
    (should-not-contain :last-key save-load/saveable-atoms))

  (it "does not contain load-menu-open"
    (should-not-contain :load-menu-open save-load/saveable-atoms)))
