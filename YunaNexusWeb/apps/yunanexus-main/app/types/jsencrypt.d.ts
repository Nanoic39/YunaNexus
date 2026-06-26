declare module "jsencrypt" {
  interface JSEncryptOptions {
    default_key_size?: string;
    default_public_exponent?: string;
  }

  export class JSEncrypt {
    constructor(options?: JSEncryptOptions);
    setPublicKey(key: string): void;
    encrypt(text: string): string | false;
  }
}
