ThisBuild / version := "0.1.0-SNAPSHOT"

ThisBuild / scalaVersion := "3.2.1"

val http4sVersion = "1.0.0-M37"

lazy val root = (project in file("."))
  .settings(
    name := "cats-sangria",
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % "3.4.2",
      "org.http4s" %% "http4s-dsl" % http4sVersion,
      "org.http4s" %% "http4s-ember-server" % http4sVersion,
      "org.http4s" %% "http4s-circe" % http4sVersion,
      "org.slf4j" % "slf4j-simple" % "2.0.5",
      "org.sangria-graphql" %% "sangria" % "3.4.1",
      "org.sangria-graphql" %% "sangria-circe" % "1.3.2",
      "io.circe" %% "circe-core" % "0.14.3",
      "io.circe" %% "circe-parser" % "0.14.3",
      "io.circe" %% "circe-generic" % "0.14.3",
      "org.typelevel" %% "log4cats-slf4j" % "2.5.0"
    ),

    scalacOptions += "-source:future",
    scalacOptions += "-Ykind-projector"
  )
