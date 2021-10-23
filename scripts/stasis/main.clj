(ns stasis.main
  (:require [babashka.fs :as fs]
            [borkdude.rewrite-edn :as r]
            [clojure.string :as string]
            [docopt.core :as docopt]))

; logic
(def ^:private +slug-tr-map+
  (zipmap "ąàáäâãåæăćčĉęèéëêĝĥìíïîĵłľńňòóöőôõðøśșšŝťțŭùúüűûñÿýçżźž"
          "aaaaaaaaaccceeeeeghiiiijllnnoooooooossssttuuuuuunyyczzz"))

(defn slug
  "Transform text into a URL slug."
  [s]
  (some-> (string/lower-case s)
          (string/escape +slug-tr-map+)
          (string/replace #"[\P{ASCII}]+" "")
          (string/replace #"[^\w\s]+" "")
          (string/replace #"\s+" "-")))

; file
(defn get-file [filepath]
  (->> filepath
       (apply fs/path)
       fs/file))

(defn create-or-file [file]
  (when-not (.exists file)
    (fs/create-file file))
  file)

(def write-file-lock (Object.))

(defn write-file! [content filepath append]
  (locking write-file-lock
    (-> filepath
        get-file
        create-or-file
        (spit content :append append))))

; db
(defn read-db []
  (-> ["src" "data.edn"]
      get-file
      slurp
      r/parse-string))

(defn save-db! [db]
  (-> db
      str
      (write-file! ["src" "data.edn"] false)))

(defn db->upsert!
  [content type index db]
  (-> db
      (r/assoc-in [type index] content)
      save-db!))

(defn db->delete!
  [type index db]
  (-> db
      (r/update type #(r/dissoc % index))
      save-db!))

(comment
  (db->upsert! {:page/name "Peperoni"} :pages "chacaracatacara" (read-db))
  (db->upsert! {:tag/name "Piriliri"} :tags :piriliri (read-db))
  (db->delete! :pages "chacaracatacara" (read-db))
  (db->delete! :tags :piriliri (read-db))
  (write-file! "# Popopo\n" ["resources" "public" "pages" "pipipi.md"] true))

(defn new-post-old [& _args]
  (let [post-title (-> (System/console)
                       (.readLine "What is the post tittle? " nil)
                       String.)
        post-description (-> (System/console)
                             (.readLine "Describe it in one sentence: " nil)
                             String.)
        timestamp (System/currentTimeMillis)
        post-slug (slug post-title)
        file-path (str "post/" post-slug ".md")]
    (println "Title: " post-title
             " Description: " post-description
             " Timestamp: " timestamp
             " Slug: " post-slug
             " Markdown:" file-path)))

; main
(def usage "Stasis
Usage:
  bb <action> [options]
  bb <action> <id> [options]
  bb -h | --help

Options:
  -h --help          Show help.
  --action <action>  Action name: [init, new:post, delete:post, new:page, delete:page, new:tag, delete:tag]
  --id <id>          Id, if action is edit/delete.
  -n --name <name>   Post/Page/Tag name.
  -d --desc <desc>   Post short description.
  -s --slug <slug>   Post/Page slug override.
  -t --tags <tags>   Post tags ids on double-quotes [eg: \":gamedev :raylib\"]. Use new:tag/delete:tag to manage tags.
")

(defn new-post
  [& _]
  (docopt/docopt
   usage
   *command-line-args*
   (fn [arg-map]
     (when (arg-map "--help")
       (println usage)
       (System/exit 0))
     (let [action (or (arg-map "<action>") (arg-map "--action"))
           id (or (arg-map "<id>") (arg-map "--id"))
           name (arg-map "--name")
           desc (arg-map "--desc")
           slug (arg-map "--slug")
           tags (arg-map "--tags")]
       (println {:action action
                 :id     id
                 :name   name
                 :desc   desc
                 :slug   slug
                 :tags   tags})))))
