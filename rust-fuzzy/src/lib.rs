use jni::EnvUnowned;
use jni::errors::LogErrorAndDefault;
use jni::objects::{JClass, JString};
use jni::sys::{jint, jintArray};
use nucleo_matcher::{Config, Matcher, Utf32String};
use nucleo_matcher::pattern::{Atom, AtomKind, CaseMatching, Normalization};

/// Score a single (haystack, needle) pair.
/// Returns the nucleo score as jint, or -1 if no match. Returns 0 on JNI error.
#[unsafe(no_mangle)]
pub extern "system" fn Java_ro_faur_explorer_quickopen_backend_NucleoNative_score<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    haystack: JString<'local>,
    needle: JString<'local>,
) -> jint {
    unowned_env.with_env(|env| -> jni::errors::Result<jint> {
        let haystack_str = haystack.try_to_string(env)?;
        let needle_str = needle.try_to_string(env)?;

        let mut matcher = Matcher::new(Config::DEFAULT);
        let haystack_u32 = Utf32String::from(haystack_str.as_str());
        let atom = Atom::new(
            &needle_str,
            CaseMatching::Ignore,
            Normalization::Smart,
            AtomKind::Fuzzy,
            false, // escape_whitespace
        );

        let score = atom
            .score(haystack_u32.slice(..), &mut matcher)
            .map(|s| s as jint)
            .unwrap_or(-1);
        Ok(score)
    }).resolve::<LogErrorAndDefault>()
}

/// Return match indices for a single (haystack, needle) pair.
/// Returns int[] of matched char positions, or null on error / empty on no match.
#[unsafe(no_mangle)]
pub extern "system" fn Java_ro_faur_explorer_quickopen_backend_NucleoNative_matchIndices<'local>(
    mut unowned_env: EnvUnowned<'local>,
    _class: JClass<'local>,
    haystack: JString<'local>,
    needle: JString<'local>,
) -> jintArray {
    unowned_env.with_env(|env| -> jni::errors::Result<jintArray> {
        let haystack_str = haystack.try_to_string(env)?;
        let needle_str = needle.try_to_string(env)?;

        let mut matcher = Matcher::new(Config::DEFAULT);
        let haystack_u32 = Utf32String::from(haystack_str.as_str());
        let atom = Atom::new(
            &needle_str,
            CaseMatching::Ignore,
            Normalization::Smart,
            AtomKind::Fuzzy,
            false,
        );

        let mut indices: Vec<u32> = Vec::new();
        // indices is NOT auto-cleared by nucleo — must clear before each call
        indices.clear();
        match atom.indices(haystack_u32.slice(..), &mut matcher, &mut indices) {
            Some(_) => {
                let int_indices: Vec<i32> = indices.iter().map(|&x| x as i32).collect();
                let arr = env.new_int_array(int_indices.len())?;
                arr.set_region(env, 0, &int_indices)?;
                Ok(arr.into_raw())
            }
            None => {
                let empty = env.new_int_array(0)?;
                Ok(empty.into_raw())
            }
        }
    }).resolve::<LogErrorAndDefault>()
}
