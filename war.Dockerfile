# syntax=docker/dockerfile:1

# Carrier image: holds the built WAR and nothing else. It is never run.
# hmdm-docker consumes it with COPY --from.
FROM scratch
COPY launcher.war /launcher.war
