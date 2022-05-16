/**
 * SVG 45 deg diagonal stripe pattern fill
 * (Used for captured territory fills)
 * props required:
 *   props.id: id key
 *   props.fillColor: background fill color
 *   props.stripeColor: stripe color
 */

'use strict';

export const StripePattern = (props) => {
    return (
        <pattern id={props.id} width="10" height="10" patternTransform="rotate(45 0 0)" patternUnits="userSpaceOnUse">
            <path
                d={"M 0,0 L 0,10 L 10,10 L 10,0 L 0,0"}
                fill={props.fillColor}
            />
            <line x1="0" y1="0" x2="0" y2="10" stroke={props.stripeColor} strokeWidth="7"/>
        </pattern>
    );
}