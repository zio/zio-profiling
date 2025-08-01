addSbtPlugin("ch.epfl.scala"                     % "sbt-bloop"        % "2.0.12")
addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"     % "0.14.3")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"    % "0.13.1")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"   % "1.11.1")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("de.heikoseeberger"                 % "sbt-header"       % "5.10.0")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.7.2")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"     % "2.5.5")
addSbtPlugin("dev.zio"                           % "zio-sbt-website"  % "0.4.0-alpha.32")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"          % "0.4.7")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.10"
