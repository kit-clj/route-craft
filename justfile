#!/usr/bin/env just --justfile
# https://github.com/casey/just

# list all options when just is called with no arguments
default:
  @just --list

# start the docker compose environment
up:
  docker compose up -d

# stop the docker compose environment
down:
  docker compose down