import Foundation
import CryptoKit
import CommonCrypto
import Security

// Same versioned wire format and synthetic fixture as Android Backup.java.
enum PortableBackup {
    static let magic=Data([80,80,66,75,1])
    static func key(_ password:String,_ salt:Data)throws->SymmetricKey{
        try require((12...128).contains(password.utf8.count)&&password.utf8.allSatisfy{(32...126).contains($0)},"Use 12–128 printable English characters")
        let bytes=Array(password.utf8);var derived=[UInt8](repeating:0,count:32)
        let status=bytes.withUnsafeBytes { pass in salt.withUnsafeBytes { s in
            CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2),pass.baseAddress?.assumingMemoryBound(to:Int8.self),bytes.count,s.baseAddress?.assumingMemoryBound(to:UInt8.self),salt.count,CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256),210000,&derived,32)
        }}
        try require(status==kCCSuccess,"Could not derive backup key")
        let result=SymmetricKey(data:derived);for i in derived.indices{derived[i]=0};return result
    }
    static func encrypt(_ plain:Data,password:String)throws->Data{
        try require(plain.count<=9_999_951,"Backup exceeds 10 MB")
        var random=[UInt8](repeating:0,count:28);try require(SecRandomCopyBytes(kSecRandomDefault,random.count,&random)==errSecSuccess,"Could not generate random backup salt")
        let header=magic+Data(random),salt=header.subdata(in:5..<21),nonce=try AES.GCM.Nonce(data:header.subdata(in:21..<33))
        let sealed=try AES.GCM.seal(plain,using:key(password,salt),nonce:nonce,authenticating:header)
        return header+sealed.ciphertext+sealed.tag
    }
    static func decrypt(_ bytes:Data,password:String)throws->Data{
        try require((49...10_000_000).contains(bytes.count)&&bytes.prefix(5)==magic,"Unsupported or invalid backup")
        let header=bytes.prefix(33),salt=bytes.subdata(in:5..<21),nonce=try AES.GCM.Nonce(data:bytes.subdata(in:21..<33))
        let sealed=try AES.GCM.SealedBox(nonce:nonce,ciphertext:bytes.subdata(in:33..<(bytes.count-16)),tag:bytes.suffix(16))
        do{return try AES.GCM.open(sealed,using:key(password,salt),authenticating:header)}
        catch{throw PilotError(message:"Incorrect passphrase or damaged backup")}
    }
}
