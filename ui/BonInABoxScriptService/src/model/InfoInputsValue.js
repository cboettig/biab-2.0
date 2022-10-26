/**
 * BON in a Box - Script service
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * The version of the OpenAPI document: 1.0.0
 * Contact: jean-michel.lord@mcgill.ca
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 *
 */

import ApiClient from '../ApiClient';
//import InfoInputsValueExample from './InfoInputsValueExample';

/**
 * The InfoInputsValue model module.
 * @module model/InfoInputsValue
 * @version 1.0.0
 */
class InfoInputsValue {
    /**
     * Constructs a new <code>InfoInputsValue</code>.
     * @alias module:model/InfoInputsValue
     */
    constructor() { 
        
        InfoInputsValue.initialize(this);
    }

    /**
     * Initializes the fields of this object.
     * This method is used by the constructors of any subclasses, in order to implement multiple inheritance (mix-ins).
     * Only for internal use.
     */
    static initialize(obj) { 
    }

    /**
     * Constructs a <code>InfoInputsValue</code> from a plain JavaScript object, optionally creating a new instance.
     * Copies all relevant properties from <code>data</code> to <code>obj</code> if supplied or a new instance if not.
     * @param {Object} data The plain JavaScript object bearing properties of interest.
     * @param {module:model/InfoInputsValue} obj Optional instance to populate.
     * @return {module:model/InfoInputsValue} The populated <code>InfoInputsValue</code> instance.
     */
    static constructFromObject(data, obj) {
        if (data) {
            obj = obj || new InfoInputsValue();

            if (data.hasOwnProperty('description')) {
                obj['description'] = ApiClient.convertToType(data['description'], 'String');
            }
            if (data.hasOwnProperty('label')) {
                obj['label'] = ApiClient.convertToType(data['label'], 'String');
            }
            if (data.hasOwnProperty('type')) {
                obj['type'] = ApiClient.convertToType(data['type'], 'String');
            }
            if (data.hasOwnProperty('options')) {
                obj['options'] = ApiClient.convertToType(data['options'], ['String']);
            }
            if (data.hasOwnProperty('range')) {
                obj['range'] = ApiClient.convertToType(data['range'], ['Number']);
            }
            if (data.hasOwnProperty('example')) {
                // JM Lord: Current version of the generator does not work when type is "oneOf" in OpenAPI spec.
                // We want the default convertToType clause to execute.
                obj['example'] = ApiClient.convertToType(data['example'], 'depends on type');
            }
        }
        return obj;
    }


}

/**
 * @member {String} description
 */
InfoInputsValue.prototype['description'] = undefined;

/**
 * @member {String} label
 */
InfoInputsValue.prototype['label'] = undefined;

/**
 * @member {String} type
 */
InfoInputsValue.prototype['type'] = undefined;

/**
 * @member {Array.<String>} options
 */
InfoInputsValue.prototype['options'] = undefined;

/**
 * @member {Array.<Number>} range
 */
InfoInputsValue.prototype['range'] = undefined;

/**
 * @member {module:model/InfoInputsValueExample} example
 */
InfoInputsValue.prototype['example'] = undefined;






export default InfoInputsValue;

