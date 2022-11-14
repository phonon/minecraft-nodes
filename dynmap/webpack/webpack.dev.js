/**
 * webpack.dev.js
 * -----------------------------------
 * Development configuration environment
 *
 * Notes:
 * - entry '@babel/polyfill' required for async/await
 */

const ReactRefreshWebpackPlugin = require('@pmmmwh/react-refresh-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');
const { merge } = require('webpack-merge');
const common = require('./webpack.common.js');
const path = require('path');
const webpack = require('webpack');

const BABEL_OPTIONS = {
	presets: [
		'@babel/preset-env',
		'@babel/preset-react',
	],
	plugins: [
		'@babel/plugin-syntax-dynamic-import',
		'react-refresh/babel',
	],
};

let mainConfig = merge(common, {
	entry: [
		'./bootstrap.js'
	],
	mode: 'development',
	devtool: 'inline-source-map',
	devServer: {
		static: {
			publicPath: '/',
			directory: path.resolve(__dirname, '..'),
		},
		compress: true,
		port: 80,
		hot: true
	},
	watchOptions: {
		ignored: /node_modules/,
	},
	output: {
		path: path.resolve(__dirname, '../build'),
		publicPath: 'http://localhost/test/', // keep as '/test' otherwise messes up file-loader public path
		filename: 'js/nodes.js',
		chunkFilename: 'js/nodes.[name].[id].[chunkhash].js',
		library: 'Nodes',
		libraryTarget: 'var',
		libraryExport: 'default'
	},
	module: {
		rules: [
			{
				test: /\.(js|jsx)$/,
				exclude: /node_modules/,
				use: {
					loader: 'babel-loader',
					options: BABEL_OPTIONS,
				}
			},
			{
				test: /\.(png|jpg|gif)$/,
				loader: 'file-loader',
				options: {
					publicPath: 'http://localhost/test/images/nodes',
					outputPath: 'images/nodes',
					name: '[name].[ext]'
				}
			},
		]
	},
	plugins: [
		new ReactRefreshWebpackPlugin(),
		// new CopyPlugin({ // uncomment if external .js libraries are needed
		// 	patterns: [
		// 		{ from: path.resolve(__dirname, '../lib') },
		// 	],
		// }),
	]

});

module.exports = mainConfig;
