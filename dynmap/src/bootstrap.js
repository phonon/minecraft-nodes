/**
 * bootstrap.js
 * ----------------------------------------------------------------
 * Asynchronously load for wasm load/parse.
 * Bootstrap handles internal dependency graph of all
 * dynamic imports, so source code can be written synchronously.
 *
 * See: https://github.com/rustwasm/wasm-pack
 */

let Nodes = {
	initialize: function(options, callback) {
		import('./nodes.js')
			.then((module) => {
				// set window value
				window.Nodes = module.default;

				// run nodes initialization
				module.default.initialize(options, callback);
			})
			.catch(err => console.error('[Nodes] Load failed', err));
	}
};

// export to webpack
export default Nodes;
