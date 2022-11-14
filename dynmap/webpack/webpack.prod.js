/**
 * webpack.prod.js
 * -----------------------------------
 * production environment on AWS
 *
 */

const { CleanWebpackPlugin } = require('clean-webpack-plugin');
const CopyPlugin = require('copy-webpack-plugin');
const BundleAnalyzerPlugin = require('webpack-bundle-analyzer').BundleAnalyzerPlugin;
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
		// 'react-refresh/babel', // no refresh in production
	],
};

let mainConfig = merge(common, {
	entry: [
		'./bootstrap.js'
	],
	mode: 'production',
	watchOptions: {
		ignored: /node_modules/,
	},
	output: {
		path: path.resolve(__dirname, '../build'),
		publicPath: '/', // keep as '/' otherwise messes up file-loader public path
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
					publicPath: '/images/nodes',
					outputPath: 'images/nodes',
					name: '[name].[ext]'
				}
			},
		]
	},
	plugins: [
		new CleanWebpackPlugin(),
		new CopyPlugin({
			patterns: [
				// { from: path.resolve(__dirname, '../lib') }, // uncomment if external .js libraries are needed
				{ from: path.resolve(__dirname, '../src/dynmap') },
			],
		})
		// new BundleAnalyzerPlugin(),
	]
});

module.exports = mainConfig;
