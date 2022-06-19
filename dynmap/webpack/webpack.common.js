/**
 * webpack.common.js
 * -----------------------------------
 * Common configuration environment
 *
 */

const path = require('path');
const webpack = require('webpack');

module.exports = {
	context: path.resolve(__dirname, '../src'),
	resolve: {
		modules: [
			'node_modules',
			path.resolve(__dirname, '../wasm'),
			path.resolve(__dirname, '../src'),
		]
	},
	output: {
		globalObject: 'self'
	},
	experiments: {
		asyncWebAssembly: true,
	},
	module: {
		rules: [
			{
				test: /\.css$/,
				use: [
					{ loader: "style-loader" },
					{ loader: "css-loader" }
				]
			},
			{
				test: /\.(svg)$/,
				use: {
					loader: 'svg-url-loader',
					options: {
						noquotes: true
					}
				}
			},
		]
	},
	plugins: [
		new webpack.ProvidePlugin({
			React: 'react',
			FileSaver: 'file-saver',
		})
	],
};
