import { Injectable } from '@angular/core';
import {
  DynamicFormControlModel,
  DynamicInputModel
} from '@ng-dynamic-forms/core';
import { Connection, Connector, FormFactoryService, StringMap, ConfigurationProperty } from '@syndesis/ui/platform';

@Injectable()
export class ConnectionConfigurationService {

  formConfig: StringMap<ConfigurationProperty>;

  constructor(private formFactory: FormFactoryService) {}

  shouldValidate(connector: Connector) {
    const tags = connector.tags || [];
    return tags.indexOf('verifier') != -1;
  }

  sanitize(data: {}): any {
    return this.formFactory.sanitizeValues(data, this.formConfig);
  }

  getFormModel(
    connection: Connection,
    readOnly: boolean
  ): DynamicFormControlModel[] {
    const config = this.formConfig = this.getFormConfig(connection);
    let controls = ['*'];
    // TODO temporary client-side hack to tweak form ordering
    switch (connection.connectorId) {
      case 'activemq':
        controls = ['brokerUrl', 'username', 'password', 'clientId', 'skipCertificateCheck', 'brokerCertificate', 'clientCertificate'];
        break;
      default:
    }
    const formModel = this.formFactory.createFormModel(config, undefined, controls);
    formModel
      .filter(model => model instanceof DynamicInputModel)
      .forEach(model => ((<DynamicInputModel>model).readOnly = readOnly));
    return formModel;
  }

  cloneObject(obj: any) {
    return JSON.parse(JSON.stringify(obj));
  }

  private getFormConfig(connection: Connection) {
    let props = {};
    if (connection.connector) {
      if (connection.connector.properties) {
        props = this.cloneObject(connection.connector.properties);
      }
      if (connection.connector.configuredProperties) {
        Object.keys(connection.connector.configuredProperties).forEach(key => {
          if (props[key]) {
            props[key].value = connection.connector.configuredProperties[key];
          }
        });
      }
      if (connection.configuredProperties) {
        Object.keys(connection.configuredProperties).forEach(key => {
          if (props[key]) {
            props[key].value = connection.configuredProperties[key];
          }
        });
      }
    }
    return props;
  }
}
