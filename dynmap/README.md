# Mineman dynmap editor for nodes meme plugin
Nodes dynmap viewer/editor layer on top on minecraft dynmap.

The overall architecture:
-   React js handles ui layout. Nodes in map are `svg` elements
    laid out by React. React DOM layer is injected into the
    dynmap html/javascript.
-   Internal nodes map world written in rust compiled to wasm
    to accelerate internal nodes map structure calculations.

Current dynmap plugin target version: `Dynmap-3.3.2-spigot.jar`.
The dynmap plugin version must match because dynmap's api/internals
can change which can screw up the nodes map injection.


# Requirements/Installation
## Requirements
1. node.js
2. Rust nightly 1.62.0+
3. wasm bindgen for Rust


## Installation
1. Download/install node.js and npm: <https://nodejs.org/en/download>

2. Install npm packages:
```
npm install
```

3. Download/install rust language: <https://www.rust-lang.org/tools/install>

4. Switch into rust nightly for this repository (required for wasm for now):
```
rustup override set nightly
```

5. Make sure rust toolchain is updated to nightly >=1.62.0:
```
rustup update nightly
```

6. Install `wasm32` target for rust:
```
rustup target add wasm32-unknown-unknown
```

7. Install rust `wasm-bindgen` cli used to generate wasm bindings
(NOTE: cli version **must** match `wasm-bindgen` version in `Cargo.toml`):
```
cargo install -f --version 0.2.80 wasm-bindgen-cli
```

8. If things install properly, run:
```
npm run wasm
```
which will build wasm bindings with no errors and print out:
```
> wasm-bindgen target/wasm32-unknown-unknown/release/wasm_main.wasm --out-dir ./wasm
```
If this works, then we can start running development environment and
build release files.


# Development
1. Run `npm run dev` in this repo root to start webpack dev environment.
2. Run `npm run wasm` to compile rust/wasm (will hot reload web page).
3. Go to `localhost/test/editor.html` for main testing page.


# Build for release
`npm run build`

1. First compiles the rust/wasm.
2. Runs webpack on the js/react.

Run in the repo root. This generates files in the `/build` directory.
Copy all generated files into the `plugins/dynmap/web` folder of the
Minecraft server.


# Folder structure
```
build/             - webpack build outputs
src/             
 ├─ assets/        - static files (e.g. icons, ...)
 ├─ dynmap/        - dynmap js overrides
 ├─ dynmap_dummy/  - dynmap local static http testing js overrides
 ├─ editor/        - editor pane ui
 ├─ ui/            - common ui lib
 └─ world/         - world rendering ui
test/              - local testing, contains a local dynmap environment
webpack/           - webpack configs
```


# Acknowledgements
This editor is built on top of dynmap: https://github.com/webbukkit/dynmap