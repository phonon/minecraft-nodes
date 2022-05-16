extern crate num_traits;
extern crate cfg_if;
extern crate fnv;
extern crate rand;
extern crate voronator;
extern crate wasm_bindgen;

#[macro_use]
extern crate bitflags;

#[macro_use]
extern crate thiserror;

mod territory;

use cfg_if::cfg_if;
use wasm_bindgen::prelude::*;

cfg_if! {
	// When the `wee_alloc` feature is enabled, use `wee_alloc` as the global allocator
	if #[cfg(feature = "wee_alloc")] {
		extern crate wee_alloc;
		#[global_allocator]
		static ALLOC: wee_alloc::WeeAlloc = wee_alloc::WeeAlloc::INIT;
	}
}

#[wasm_bindgen]
extern {
    #[wasm_bindgen(js_namespace = console)]
    fn log(s: &str);
}

// test function
#[wasm_bindgen]
pub fn greet() {
	log("hello world!");
}
