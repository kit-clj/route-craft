# route-craft

WIP experiments in route generation based on PostgreSQL schema. Not ready for any real use.

## Rationale

## Usage

### Configuration Options

### Supported handlers

- `:insert-one` => `POST /`
- `:get-by-pk` => `GET /:{pk}`
- `:update-by-pk` => `PUT /:{pk}`
- `:delete-by-pk` => `DELETE /:{pk}`

#### Planned handlers

- `:get-many` => `GET /`
- `:update-by-query` => `PUT /`
- `:delete-by-query` => `DELETE /`

#### Rough ideas for handlers

- `:insert-many` => `POST /` with header?
- `:upsert-by-pk` => `PUT /:{pk}` with header?

## Auth and Permissions

## Query Parameters

## When not to use this

- Very high throughput APIs
  - There is overhead with permission checking and other things that could be avoided with a more direct approach
- More complex handlers should be managed via custom requests
  - Goal of route-craft is to handle the "80%" case of CRUD operations on database tables while permitting extensibility for the rest

## License

Copyright © 2023

Released under the MIT license.