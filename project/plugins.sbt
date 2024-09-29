addSbtPlugin("ch.epfl.scala"                     % "sbt-bloop"        % "2.0.2")
addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"     % "0.13.0")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"    % "0.12.0")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"   % "1.6.1")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("de.heikoseeberger"                 % "sbt-header"       % "5.10.0")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.6.1")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"     % "2.5.2")
addSbtPlugin("dev.zio"                           % "zio-sbt-website"  % "0.4.0-alpha.28")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"          % "0.4.7")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.8"
