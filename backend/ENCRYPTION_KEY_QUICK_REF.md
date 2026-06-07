# Password Encryption - Quick Reference

## Environment Variable

```bash
export PLATFORM_ENCRYPTION_KEY="your-32-char-random-key-here"
```

## Requirements

- **Minimum length**: 16 characters
- **Recommended**: 32+ characters
- **Required in**: Production, Staging
- **Optional in**: Development, Test (has fallback)

## How It Works

### Priority Order
1. Application property: `platform.account.encryption-key`
2. Environment variable: `PLATFORM_ENCRYPTION_KEY`
3. Fallback key (dev/test only)

### Environment Behavior

| Environment | Key Required? | Fallback Available? |
|-------------|---------------|---------------------|
| Production  | ✅ Yes        | ❌ No               |
| Staging     | ✅ Yes        | ❌ No               |
| Development | ⚠️ Optional  | ✅ Yes (with warning) |
| Test        | ⚠️ Optional  | ✅ Yes (with warning) |

## Quick Start

### Local Development
```bash
# Just run the application - no setup needed!
mvn spring-boot:run
```

### Production Setup
```bash
# 1. Generate a secure key (32+ characters)
export PLATFORM_ENCRYPTION_KEY="$(openssl rand -base64 32)"

# 2. Verify it's set
echo $PLATFORM_ENCRYPTION_KEY

# 3. Start application
java -jar app.jar --spring.profiles.active=prod
```

## Testing

### Run Unit Tests
```bash
cd backend
mvn test -Dtest=PasswordEncryptionUtilTest
```

### Run Integration Tests
```bash
cd backend
mvn test -Dtest=PasswordEncryptionUtilIntegrationTest
```

### Verify Fix
```bash
cd backend
./verify-encryption-key-fix.sh
```

## Common Issues

### Issue: "PLATFORM_ENCRYPTION_KEY required"
**Cause**: Running in production without key
**Solution**: Set environment variable

### Issue: "Key must be at least 16 characters"
**Cause**: Key too short
**Solution**: Use longer key (32+ recommended)

### Issue: Warning about fallback key
**Cause**: No key set in dev/test environment
**Solution**: This is expected! Or set PLATFORM_ENCRYPTION_KEY to override

## Code Examples

### Encrypt a Password
```java
@Autowired
private PasswordEncryptionUtil passwordEncryptionUtil;

public void saveAccount(String password) {
    String encrypted = passwordEncryptionUtil.encrypt(password);
    // Save encrypted password
}
```

### Decrypt a Password
```java
public String getPassword(String encryptedPassword) {
    return passwordEncryptionUtil.decrypt(encryptedPassword);
}
```

### Validate Key
```java
if (passwordEncryptionUtil.isKeyValid()) {
    // Safe to use encryption
}
```

## Files Changed

- `PasswordEncryptionUtil.java` - Main implementation
- `PasswordEncryptionUtilTest.java` - Unit tests (20 tests)
- `PasswordEncryptionUtilIntegrationTest.java` - Integration tests (3 tests)

## Documentation

- `PASSWORD_ENCRYPTION_FIX_SUMMARY.md` - Full implementation details
- `ENCRYPTION_KEY_BEFORE_AFTER.md` - Before/after comparison
- `SECURITY_FIX_CHECKLIST.md` - Verification checklist

## Support

For issues or questions:
1. Check the test files for usage examples
2. Review the JavaDoc in `PasswordEncryptionUtil.java`
3. Run the verification script to diagnose problems
