use std::io::Write;
use std::net::TcpStream;
use std::os::raw::{c_char, c_int, c_longlong, c_void};
use std::ptr;

// Declared by hand for clarity; you can also generate these from
// ../../target/libfaradn.h with bindgen.
extern "C" {
    fn graal_create_isolate(
        params: *const c_void,
        isolate: *mut *mut c_void,
        thread: *mut *mut c_void,
    ) -> c_int;
    fn graal_tear_down_isolate(thread: *mut c_void) -> c_int;
    fn faradn_render(
        thread: *mut c_void,
        html: *const c_char,
        profile: *const c_char,
        out_buffer: *mut *mut u8,
        out_length: *mut c_longlong,
    ) -> c_int;
    fn faradn_free(thread: *mut c_void, buffer: *mut u8);
}

fn main() {
    let html = b"<h1>Receipt</h1><p>Total: <b>10,00</b></p>\0";
    let profile = b"tm-t88v\0";
    let addr = std::env::args().nth(1).unwrap_or_else(|| "192.168.1.50:9100".into());

    let bytes = unsafe {
        let mut isolate: *mut c_void = ptr::null_mut();
        let mut thread: *mut c_void = ptr::null_mut();
        assert_eq!(graal_create_isolate(ptr::null(), &mut isolate, &mut thread), 0);

        let mut buffer: *mut u8 = ptr::null_mut();
        let mut length: c_longlong = 0;
        let rc = faradn_render(
            thread,
            html.as_ptr() as *const c_char,
            profile.as_ptr() as *const c_char,
            &mut buffer,
            &mut length,
        );
        assert_eq!(rc, 0, "faradn_render failed");

        let bytes = std::slice::from_raw_parts(buffer, length as usize).to_vec();
        faradn_free(thread, buffer);
        graal_tear_down_isolate(thread);
        bytes
    };

    let mut stream = TcpStream::connect(&addr).expect("connect to printer");
    stream.write_all(&bytes).expect("write to printer");
    println!("sent {} bytes to {}", bytes.len(), addr);
}
