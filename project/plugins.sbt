addSbtPlugin("ch.epfl.scala"                     % "sbt-bloop"        % "1.5.13")
addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"     % "0.12.0")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"    % "0.11.0")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"   % "1.5.12")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.5.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("de.heikoseeberger"                 % "sbt-header"       % "5.10.0")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.3.8")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"     % "2.5.2")
addSbtPlugin("dev.zio"                           % "zio-sbt-website"  % "0.4.0-alpha.22")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"          % "0.4.7")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "2.7"
