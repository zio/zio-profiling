addSbtPlugin("ch.epfl.scala"                     % "sbt-bloop"        % "2.0.16")
addSbtPlugin("ch.epfl.scala"                     % "sbt-scalafix"     % "0.14.4")
addSbtPlugin("com.eed3si9n"                      % "sbt-buildinfo"    % "0.13.1")
addSbtPlugin("com.github.sbt"                    % "sbt-ci-release"   % "1.11.2")
addSbtPlugin("com.github.sbt"                    % "sbt-unidoc"       % "0.6.0")
addSbtPlugin("com.thoughtworks.sbt-api-mappings" % "sbt-api-mappings" % "3.0.2")
addSbtPlugin("com.github.sbt"                    % "sbt-header"       % "5.11.0")
addSbtPlugin("org.scalameta"                     % "sbt-mdoc"         % "2.8.0")
addSbtPlugin("org.scalameta"                     % "sbt-scalafmt"     % "2.5.6")
addSbtPlugin("dev.zio"                           % "zio-sbt-website"  % "0.4.0-alpha.36")
addSbtPlugin("pl.project13.scala"                % "sbt-jmh"          % "0.4.8")

libraryDependencies += "org.snakeyaml" % "snakeyaml-engine" % "3.0"
