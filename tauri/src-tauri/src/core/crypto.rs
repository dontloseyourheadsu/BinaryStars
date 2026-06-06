use aes_gcm::{
    aead::{Aead, KeyInit},
    Aes256Gcm, Nonce
};
use sha2::{Sha256, Digest};
use base64::{Engine as _, engine::general_purpose};

fn derive_key(password: &str) -> aes_gcm::Key<Aes256Gcm> {
    let mut hasher = Sha256::new();
    hasher.update(password.as_bytes());
    let result = hasher.finalize();
    *aes_gcm::Key::<Aes256Gcm>::from_slice(&result)
}

pub fn encrypt(data: &[u8], password: &str) -> Result<String, String> {
    if password.is_empty() {
        return Ok(general_purpose::STANDARD.encode(data));
    }
    let key = derive_key(password);
    let cipher = Aes256Gcm::new(&key);
    
    // Generate random nonce using UUID v4
    let uuid_bytes = uuid::Uuid::new_v4().into_bytes();
    let nonce_bytes = &uuid_bytes[0..12];
    let nonce = Nonce::from_slice(nonce_bytes);
    
    let ciphertext = cipher.encrypt(nonce, data).map_err(|e| e.to_string())?;
    
    let mut combined = Vec::with_capacity(12 + ciphertext.len());
    combined.extend_from_slice(nonce_bytes);
    combined.extend_from_slice(&ciphertext);
    
    Ok(general_purpose::STANDARD.encode(combined))
}

pub fn decrypt(encrypted_base64: &str, password: &str) -> Result<Vec<u8>, String> {
    let decoded = general_purpose::STANDARD
        .decode(encrypted_base64)
        .map_err(|e| e.to_string())?;

    if password.is_empty() {
        return Ok(decoded);
    }

    let key = derive_key(password);
    let cipher = Aes256Gcm::new(&key);
    
    if decoded.len() < 12 {
        return Err("Ciphertext too short".to_string());
    }
    
    let (nonce_bytes, ciphertext) = decoded.split_at(12);
    let nonce = Nonce::from_slice(nonce_bytes);
    
    let plaintext = cipher.decrypt(nonce, ciphertext).map_err(|e| e.to_string())?;
    Ok(plaintext)
}
