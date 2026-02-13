/**********************************************************************
* Compile with:
* gcc -Wall packet.c -lpcap
*
* Usage:
* a.out (# of packets) "filter string"
*
**********************************************************************/

#include <pcap.h>
#include <stdio.h>
#include <ctype.h>
#include <stdlib.h>
#include <errno.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <netinet/ether.h>
#include <netinet/ip.h>		/* to parse IP headers.  */
#include <netinet/tcp.h>	/* to parse TCP headers. */
#include <string.h>

/* default snap length (maximum bytes per packet to capture) */
#define SNAP_LEN                1518
#define IP_HL(ip)               ((ip)->ip_hl)
#define IP_V(ip)                ((ip)->ip_v)
#define UNLIMITED               -1
#define MAX_DEVICES             50
#define BUFSZ                   256
#define PROMISCUOUS_MODE        1
#define TIMEOUT_MS              1000

u_int16_t handle_ethernet
(u_char * args, const struct pcap_pkthdr *pkthdr,
 const u_char * packet);

void
print_payload(const u_char *payload, int len);

void
print_hex_ascii_line(const u_char *payload, int len, int offset);


/*
 * print data in rows of 16 bytes: offset   hex   ascii
 *
 * 00000   47 45 54 20 2f 20 0f 9d 5a   GET / HTTP/1.1..
 */
void
print_hex_ascii_line(const u_char *payload, int len, int offset)
{
    int i;
    int gap;
    const u_char *ch;

    /* offset */
    printf("%05d   ", offset);

    /* hex */
    ch = payload;
    for(i = 0; i < len; i++)
    {
        printf("%02x ", *ch);
        ch++;
        /* print extra space after 8th byte for visual aid */
        if (i == 7)
            printf(" ");
    }
    /* print space to handle line less than 8 bytes */
    if (len < 8)
        printf(" ");

    /* fill hex gap with spaces if not full line */
    if (len < 16)
    {
        gap = 16 - len;
        for (i = 0; i < gap; i++)
        {
            printf("   ");
        }
    }
    printf("   ");

    /* ascii (if printable) */
    ch = payload;
    for(i = 0; i < len; i++)
    {
        if (isprint(*ch))
            printf("%c", *ch);
        else
            printf(".");
        ch++;
    }

    printf("\n");

    return;
}

/*
 * print packet payload data (avoid printing binary data)
 */
void
print_payload(const u_char *payload, int len)
{

    int len_rem = len;
    int line_width = 16;   /* number of bytes per line */
    int line_len;
    int offset = 0;     /* zero-based offset counter */
    const u_char *ch = payload;

    if (len <= 0)
        return;

    /* data fits on one line */
    if (len <= line_width)
    {
        print_hex_ascii_line(ch, len, offset);
        return;
    }

    /* data spans multiple lines */
    for ( ;; )
    {
        /* compute current line length */
        line_len = line_width % len_rem;
        /* print line */
        print_hex_ascii_line(ch, line_len, offset);
        /* compute total remaining */
        len_rem = len_rem - line_len;
        /* shift pointer to remaining bytes to print */
        ch = ch + line_len;
        /* add offset */
        offset = offset + line_width;
        /* check if we have line width chars or less */
        if (len_rem <= line_width)
        {
            /* print last line and get out */
            print_hex_ascii_line(ch, len_rem, offset);
            break;
        }
    }

    return;
}

/* process ethernet headers */
void
parse_callback(u_char * args, const struct pcap_pkthdr *pkthdr,
               const u_char * packet)
{
    struct ether_header *eptr;	/* net/ethernet.h */
    u_int16_t type;

    /* process ethernet header... */
    eptr = (struct ether_header *) packet;

    /* 
    
        fprintf(stdout, "ethernet src: %s",
            ether_ntoa((const struct ether_addr *) &eptr->ether_shost));
        fprintf(stdout, " dst: %s ",
            ether_ntoa((const struct ether_addr *) &eptr->ether_dhost));

    */

    /*
     * In order to allow packets using Ethernet v2 framing and packets
     * using the IEEE 802.3 framing to be used on the same Ethernet
     * segment, a unifying standard (IEEE 802.3x-1997) was introduced that
     * required that EtherType values be greater than or equal to 1536
     * (0x0600). That value was chosen because the maximum length (MTU) of
     * the data field of an Ethernet 802.3 frame is 1500 bytes. Thus,
     * values of 1500 and below for this field indicate that the field is
     * used as the size of the payload of the Ethernet Frame while values
     * of 1536 and above indicate that the field is used to represent
     * EtherType. The interpretation of values 1501–1535, inclusive, is
     * undefined.[1]
     */

    type = ntohs(eptr->ether_type);
    /* printf(" ETHERTYPE=0x%x\n", type); */

    if (type == ETHERTYPE_IP)
    {
        /* handle IP packet */
        struct iphdr *ip_hdr;	/* to get IP protocol data.  */
        struct tcphdr *tcp_hdr;	/* to get TCP protocol data. */
        char src_ip[100], dst_ip[100];
        int src_port, dst_port;
        char *tcp_payload;
        int size_ip, size_tcp, size_payload;

        /* we're only interested in TCP packets. */
        ip_hdr = (struct iphdr *) packet;	/* the captured data is an
						 * IP packet. */

        /* define/compute ip header offset */
        ip_hdr = (struct iphdr *)(packet + sizeof(struct ether_header));
        size_ip = (ip_hdr->ihl)*4;
        if (size_ip < 20)
        {
            printf("   * Invalid IP header length: %u bytes\n", size_ip);
            return;
        }

        /*
         * network-byte-order binary data
         */
        inet_ntop(AF_INET, &ip_hdr->saddr, src_ip, sizeof(src_ip));
        inet_ntop(AF_INET, &ip_hdr->daddr, dst_ip, sizeof(dst_ip));

        if (ip_hdr->protocol != IPPROTO_TCP)
        {
            /* printf("ip_hdr->protocol=%d\n", ip_hdr->protocol); */
            return;
        }

        printf("Processing TCP Packet\n");

        /*
           lets get the port numbers - the payload of the IP packet is
           TCP. NOTE: in IP, the ihl (IP Header Length) field contains
           the number of 4-octet chunks composing the IP packet's header
         */

        tcp_hdr = (struct tcphdr *) (packet + sizeof(struct ether_header) + ip_hdr->ihl * 4);
        size_tcp = (tcp_hdr->th_off)*4;
        if (size_tcp < 20)
        {
            printf("   * Invalid TCP header length: %u bytes\n", size_tcp);
            return;
        }

        src_port = ntohs(tcp_hdr->source);	/* ports are in network byte order. */
        dst_port = ntohs(tcp_hdr->dest);

        printf("PACKET: src %s:%d, dst %s:%d\n", src_ip, src_port, dst_ip,
               dst_port);

        /* define/compute tcp payload (segment) offset */
        tcp_payload = (char *)(packet +  sizeof(struct ether_header) + size_ip + size_tcp);

        /* compute tcp payload (segment) size */
        size_payload = ntohs(ip_hdr->tot_len) - (size_ip + size_tcp);

        /*
         * Print payload data; it might be binary, so don't just
         * treat it as a string.
        */
        if (size_payload > 0)
        {
            printf("   Payload (%d bytes):\n", size_payload);
            print_payload((const u_char *)tcp_payload, size_payload);
        }
    }
    else if (type == ETHERTYPE_ARP)
    {
        /* discard */
    }
    else if (type == ETHERTYPE_REVARP)
    {
        /* discard */
    }
}

int main(int argc, char **argv)
{
    pcap_if_t *alldevsp, *device;
    pcap_t *descr;
    char *dev, devs[MAX_DEVICES][BUFSZ*4];
    char errbuf[PCAP_ERRBUF_SIZE];
    struct bpf_program fp;	/* hold compiled program */
    bpf_u_int32 maskp;		/* subnet mask */
    bpf_u_int32 netp;		/* ip */
    u_char *args = NULL;
    int ndevices = 0, deviceindex = 0;

    if (argc < 1)
    {
        fprintf(stdout, "Usage: %s \"filter\"\n", argv[0]);
        return 0;
    }

    /* get the list of available devices */
    printf("Finding available devices ... \n");
    if (pcap_findalldevs(&alldevsp, errbuf))
    {
        printf("Error finding devices : %s\n", errbuf);
        exit(1);
    }

    printf("\nTop Devices are:\n");
    ndevices = 0;
    for (device = alldevsp; device != NULL && ndevices < MAX_DEVICES ; device = device->next)
    {
        printf("%d. %s - %s\n", ndevices, device->name, device->description);
        if (device->name != NULL)
            strcpy(devs[ndevices], device->name);
        else
        {
            printf("Null device\n");
            exit(1);
        }

        ndevices++;
    }

    printf("Enter the number of the device you want to sniff : ");
    scanf("%d", &deviceindex);
    dev = devs[deviceindex];
    printf("Grabbing %s\n", dev);

    /* grab a device to peak into ... */
    dev = pcap_lookupdev(errbuf);
    if (dev == NULL)
    {
        printf("%s\n", errbuf);
        exit(1);
    }

    /* ask pcap for the network address and mask of the device */
    pcap_lookupnet(dev, &netp, &maskp, errbuf);

    /* open device for reading. NOTE: defaulting to promiscuous mode */
    descr = pcap_open_live(dev, SNAP_LEN, PROMISCUOUS_MODE, TIMEOUT_MS, errbuf);
    if (descr == NULL)
    {
        printf("pcap_open_live(): %s\n", errbuf);
        exit(1);
    }

    if (argc > 1)
    {
        /* compile non-optimized */
        if (pcap_compile(descr, &fp, argv[1], 0, netp) == -1)
        {
            fprintf(stderr, "Error calling pcap_compile\n");
            exit(1);
        }

        /* set the filter */
        if (pcap_setfilter(descr, &fp) == -1)
        {
            fprintf(stderr, "Error setting filter\n");
            exit(1);
        }
    }

    /* loop */
    pcap_loop(descr, UNLIMITED, parse_callback, args);

    fprintf(stdout, "\nfinished\n");
    return 0;
}
